use reqwest;
use tokio::fs::File as AsyncFile;
use tokio::io::AsyncWriteExt;
use tokio::process::Command;

pub(crate) async fn download_file(download_url: &str, local_filename: &str, os_type: &str, auto_update_supported: bool) -> Result<(), Box<dyn std::error::Error>> {
    let download_dir = dirs::download_dir().ok_or("[AR] • Failed to determine download directory")?;
    let full_path = download_dir.join(local_filename);
    let response = reqwest::get(download_url).await?;
    let bytes = response.bytes().await?;
    let mut dest_file = AsyncFile::create(&full_path).await?;
    dest_file.write_all(&bytes).await?;
    println!("[AR] • File downloaded to: {:?}", full_path);
    if auto_update_supported {
        let status;
        if os_type.to_lowercase() == "Windows".to_lowercase() {
            status = Command::new("explorer")
                .arg(download_dir.display().to_string())
                .status()
                .await
                .expect("[AR] • Failed to open downloads folder");
        } else if os_type.to_lowercase() == "MacOS".to_lowercase() {
            status = Command::new("open")
                .arg(full_path.to_str().unwrap_or_default())
                .status()
                .await
                .expect("[AR] • Failed to execute command");
        } else {
            status = Command::new(".")
                .arg(full_path.to_str().unwrap_or_default())
                .status()
                .await
                .expect("[AR] • Failed to execute command");
        }
        if status.success() {
            println!("[AR] • File opened successfully!");
        } else {
            eprintln!("[AR] • Failed to open the file. Exit code: {:?}", status.code());
        }
    }
    Ok(())
}