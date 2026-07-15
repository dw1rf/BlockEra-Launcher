use std::{net::SocketAddr, sync::Arc};

use base64::{Engine, engine::general_purpose::STANDARD};
use serde::Serialize;
use serde_json::json;
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::{TcpListener, TcpStream},
    task::AbortHandle,
};
use uuid::Uuid;

use crate::{
    api::minecraft_skins::ActiveOfflineSkin, state::MinecraftSkinVariant,
};

const PUBLIC_KEY: &str = "-----BEGIN PUBLIC KEY-----\nMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA0IkXN4USVBJvzrA3vi8y\nANUEUh9PMmPSWwFS5JccwlDZvw5ymMPuB8S69d6p4I8Ij6lkQkg8izTr3njJ4Z5k\nelDH+zClzv/LuYnEvUzaA0aGwHoH4sDUeUm34bK44By/6/ZoImKfDJmjfN/0lEOQ\nwZJL1vtDNAseSkZRPxUgBydhVqNBX9SYSfl2M5CBz8QHRe8hCI3QAMaFfqDu3uTJ\n0lPJ1HZRCTXHAMgiB2ArdgtU7rx1emga/o8Dx3LU/lV+FuKM94xaRFSreMZWluz1\nEjGcsC6je1Ah89aO0jYIHlOxzc1LB2uZaWryBaZ86uxL7EA7qyZG+mV6Y0sq7lSm\nUOYQiPInkHfEYrj+VA2gLZPP7mlNv8Xlo5cDvEaQL5Z8vOoB4xi8cY8vXSmAOR5g\neNtkm0NXT5GbZgP1NkkgFhnE4NUrjO44TzjC5enI9wfe0pOZY0k4bHY6crQwL9Rc\ntLSHz321FnYF85+yPZWU3DhsGnmfGV9rJJ2h/Fr3k85iD/0ohyWDneZECOsP2EKH\n6+L5JEcnejfvQW5S2s0M50np86Pu5gZ+c1pWTWomv5LgwFvARDRu1uFICh1RiCsv\nx6Ww85HYoQ8dJMiVIDMLe4+Zzext+08u2dM86/Mwpj/1yBzGTBw2t8y/2j8OWYi9\nh57uqbTpmpcI8dftnJdl3osCAwEAAQ==\n-----END PUBLIC KEY-----";

#[derive(Debug, Clone, Serialize)]
pub struct OfflineSkinServerInfo {
    pub api_root: String,
    pub texture_key: String,
    pub injector_enabled: bool,
}

#[derive(Debug)]
pub struct OfflineSkinServer {
    info: OfflineSkinServerInfo,
    abort_handle: AbortHandle,
}

impl OfflineSkinServer {
    pub async fn start(
        profile_id: Uuid,
        profile_name: String,
        skin: ActiveOfflineSkin,
    ) -> crate::Result<Self> {
        let listener = TcpListener::bind(("127.0.0.1", 0)).await?;
        let address = listener.local_addr()?;
        let api_root = format!("http://{address}/");
        let texture_url =
            format!("http://{address}/textures/{}", skin.texture_key);
        let profile_id_string = profile_id.simple().to_string();
        let model = match skin.variant {
            MinecraftSkinVariant::Slim => Some("slim"),
            _ => None,
        };
        let texture_payload = json!({
            "timestamp": chrono::Utc::now().timestamp_millis(),
            "profileId": profile_id_string,
            "profileName": profile_name,
            "textures": {
                "SKIN": {
                    "url": texture_url,
                    "metadata": model.map(|model| json!({ "model": model })).unwrap_or_else(|| json!({}))
                }
            }
        });
        let texture_property =
            STANDARD.encode(serde_json::to_vec(&texture_payload)?);
        let profile = Arc::new(json!({
            "id": profile_id.simple().to_string(),
            "name": profile_name,
            "properties": [{ "name": "textures", "value": texture_property }]
        }));
        let skin_bytes = Arc::new(skin.texture);
        let texture_key = skin.texture_key.clone();

        let task = tokio::spawn(async move {
            loop {
                let Ok((stream, _)) = listener.accept().await else {
                    break;
                };
                let profile = Arc::clone(&profile);
                let skin_bytes = Arc::clone(&skin_bytes);
                let texture_key = texture_key.clone();
                tokio::spawn(async move {
                    if let Err(error) = handle_connection(
                        stream,
                        address,
                        &texture_key,
                        profile,
                        skin_bytes,
                    )
                    .await
                    {
                        tracing::debug!("Offline skin request failed: {error}");
                    }
                });
            }
        });

        Ok(Self {
            info: OfflineSkinServerInfo {
                api_root,
                texture_key: skin.texture_key,
                injector_enabled: true,
            },
            abort_handle: task.abort_handle(),
        })
    }

    pub fn info(&self) -> &OfflineSkinServerInfo {
        &self.info
    }
}

impl Drop for OfflineSkinServer {
    fn drop(&mut self) {
        self.abort_handle.abort();
    }
}

async fn handle_connection(
    mut stream: TcpStream,
    address: SocketAddr,
    texture_key: &str,
    profile: Arc<serde_json::Value>,
    skin_bytes: Arc<Vec<u8>>,
) -> std::io::Result<()> {
    let mut request = vec![0_u8; 16 * 1024];
    let read = stream.read(&mut request).await?;
    if read == 0 {
        return Ok(());
    }
    let first_line = String::from_utf8_lossy(&request[..read])
        .lines()
        .next()
        .unwrap_or_default()
        .to_string();
    let mut parts = first_line.split_whitespace();
    let method = parts.next().unwrap_or_default();
    let path = parts.next().unwrap_or("/").split('?').next().unwrap_or("/");

    if path == format!("/textures/{texture_key}") {
        return respond(&mut stream, "200 OK", "image/png", &skin_bytes).await;
    }

    let body = if path == "/" {
        json!({
            "meta": {
                "implementationName": "blockera-offline-skins",
                "implementationVersion": env!("CARGO_PKG_VERSION"),
                "serverName": "BlockEra Offline Skins",
                "feature.non_email_login": true
            },
            "skinDomains": ["127.0.0.1", "localhost"],
            "signaturePublickey": PUBLIC_KEY
        })
    } else if path.starts_with("/sessionserver/session/minecraft/profile/")
        || path == "/sessionserver/session/minecraft/hasJoined"
    {
        (*profile).clone()
    } else if path == "/api/profiles/minecraft" {
        json!([{ "id": profile["id"], "name": profile["name"] }])
    } else if method == "POST"
        && path == "/sessionserver/session/minecraft/join"
    {
        return respond(&mut stream, "204 No Content", "text/plain", &[]).await;
    } else {
        json!({ "error": "NotFound", "errorMessage": format!("Unknown BlockEra skin endpoint: {path}"), "address": address.to_string() })
    };
    let status = if body.get("error").is_some() {
        "404 Not Found"
    } else {
        "200 OK"
    };
    respond(
        &mut stream,
        status,
        "application/json; charset=utf-8",
        &serde_json::to_vec(&body).unwrap_or_default(),
    )
    .await
}

async fn respond(
    stream: &mut TcpStream,
    status: &str,
    content_type: &str,
    body: &[u8],
) -> std::io::Result<()> {
    let headers = format!(
        "HTTP/1.1 {status}\r\nContent-Type: {content_type}\r\nContent-Length: {}\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n",
        body.len()
    );
    stream.write_all(headers.as_bytes()).await?;
    stream.write_all(body).await?;
    stream.shutdown().await
}
