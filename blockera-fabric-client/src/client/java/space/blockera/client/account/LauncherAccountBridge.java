package space.blockera.client.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal loopback bridge to the launcher process.
 *
 * <p>The protocol intentionally exposes account labels only. Authentication and
 * refresh tokens never leave the launcher; selecting an account requests a clean
 * relaunch of the same instance.</p>
 */
public final class LauncherAccountBridge {
    public record Account(String uuid, String username, String accountType, boolean active) {
    }

    private static final LauncherAccountBridge INSTANCE = new LauncherAccountBridge();

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Blockera launcher account bridge");
        thread.setDaemon(true);
        return thread;
    });
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    private LauncherAccountBridge() {
    }

    public static LauncherAccountBridge get() {
        return INSTANCE;
    }

    public boolean isAvailable() {
        return System.getProperty("modrinth.internal.ipc.host") != null
            && System.getProperty("modrinth.internal.ipc.port") != null;
    }

    public CompletableFuture<List<Account>> listAccounts() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonArray response = call("blockera.accounts.list", new JsonArray()).getAsJsonArray();
                List<Account> accounts = new ArrayList<>(response.size());
                for (JsonElement element : response) {
                    JsonObject account = element.getAsJsonObject();
                    accounts.add(new Account(
                        account.get("uuid").getAsString(),
                        account.get("username").getAsString(),
                        account.get("account_type").getAsString(),
                        account.get("active").getAsBoolean()
                    ));
                }
                return List.copyOf(accounts);
            } catch (IOException error) {
                throw new AccountBridgeException(error);
            }
        }, worker);
    }

    public CompletableFuture<Void> switchAccount(String uuid) {
        return CompletableFuture.runAsync(() -> {
            try {
                JsonArray arguments = new JsonArray();
                arguments.add(uuid);
                JsonElement response = call("blockera.accounts.switch", arguments);
                if (!response.isJsonObject()
                    || !response.getAsJsonObject().get("accepted").getAsBoolean()) {
                    throw new IOException("Launcher rejected account switch");
                }
            } catch (IOException error) {
                throw new AccountBridgeException(error);
            }
        }, worker);
    }

    private synchronized JsonElement call(String method, JsonArray arguments) throws IOException {
        ensureConnected();
        String requestId = UUID.randomUUID().toString();
        JsonObject request = new JsonObject();
        request.addProperty("id", requestId);
        request.addProperty("method", method);
        request.add("args", arguments);
        writer.write(request.toString());
        writer.newLine();
        writer.flush();

        String line;
        while ((line = reader.readLine()) != null) {
            JsonObject response = JsonParser.parseString(line).getAsJsonObject();
            if (!requestId.equals(response.get("id").getAsString())) {
                continue;
            }
            if (response.has("error")) {
                throw new IOException(response.get("error").getAsString());
            }
            return response.has("response") ? response.get("response") : new JsonObject();
        }
        disconnect();
        throw new IOException("Launcher connection closed");
    }

    private void ensureConnected() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        String host = System.getProperty("modrinth.internal.ipc.host");
        String portValue = System.getProperty("modrinth.internal.ipc.port");
        if (host == null || portValue == null) {
            throw new IOException("Launcher IPC is unavailable");
        }
        int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException error) {
            throw new IOException("Launcher IPC port is invalid", error);
        }
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 2_000);
        socket.setSoTimeout(5_000);
        reader = new BufferedReader(new InputStreamReader(
            socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(
            socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    private void disconnect() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // The launcher is already gone.
            }
        }
        socket = null;
        reader = null;
        writer = null;
    }

    private static final class AccountBridgeException extends RuntimeException {
        private AccountBridgeException(IOException cause) {
            super(cause);
        }
    }
}
