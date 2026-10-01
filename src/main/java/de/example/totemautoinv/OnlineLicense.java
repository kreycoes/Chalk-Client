package de.example.totemautoinv;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.Minecraft;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.*;

/** Only server-confirmed leases unlock modules. No credentials are sent to Cloudflare. */
public final class OnlineLicense {
    private static final String BASE = "https://chalk-license-api.jasonafelt641.workers.dev";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Chalk-license"); t.setDaemon(true); return t;
    });
    private static volatile long leaseUntil, trialUntil;
    private static volatile boolean expired, busy;
    private static volatile UUID account;
    private static volatile String status = "Locked";
    private static String token = "", acceptedCode = "";
    private static long tokenUntil;
    private static boolean started;
    private OnlineLicense() {}

    public static synchronized void initialize() {
        if (started) return;
        started = true;
        WORKER.scheduleWithFixedDelay(() -> {
            if (acceptedCode.isEmpty() || busy) return;
            check(acceptedCode, false);
        }, 60, 60, TimeUnit.SECONDS);
    }

    public static void submit(String raw) {
        initialize();
        String code = raw.trim().toUpperCase(Locale.ROOT).replace("-", "");
        if (!code.matches(BetaTrialManager.isBetaEdition() ? "[A-Z0-9]{8}" : "[A-Z0-9]{12}")) return;
        WORKER.execute(() -> check(code, true));
    }

    private static void check(String code, boolean notify) {
        busy = true;
        status = "Checking";
        Minecraft mc = Minecraft.getInstance();
        var user = mc.getUser();
        UUID checkingAccount = user.getProfileId();
        try {
            JsonObject result;
            if (!code.equals(acceptedCode) || !checkingAccount.equals(account) || System.nanoTime() >= tokenUntil) {
                JsonObject request = new JsonObject();
                request.addProperty("username", user.getName()); request.addProperty("code", code);
                JsonObject challenge = post("/challenge", request);
                String id = challenge.get("challenge").getAsString();
                if (!id.matches("[a-f0-9]{40}")) throw new IllegalStateException();
                // Mojang's own authlib sends the access token only to Mojang's session server.
                mc.services().sessionService().joinServer(checkingAccount, user.getAccessToken(), id);
                JsonObject proof = new JsonObject(); proof.addProperty("challenge", id);
                result = post("/activate", proof);
                token = result.get("token").getAsString();
                tokenUntil = System.nanoTime() + TimeUnit.MINUTES.toNanos(50);
            } else {
                JsonObject request = new JsonObject(); request.addProperty("token", token);
                result = post("/check", request);
            }
            String expected = BetaTrialManager.isBetaEdition() ? "beta" : "lifetime";
            if (!expected.equals(result.get("edition").getAsString())) throw new IllegalStateException();
            long remaining = result.get("remainingMillis").getAsLong();
            if (expected.equals("beta") && (remaining <= 0 || remaining > 86400000L)) throw new IllegalStateException();
            long now = System.nanoTime();
            trialUntil = remaining < 0 ? Long.MAX_VALUE : now + TimeUnit.MILLISECONDS.toNanos(remaining);
            leaseUntil = Math.min(trialUntil, now + TimeUnit.MINUTES.toNanos(2));
            account = checkingAccount; acceptedCode = code; expired = false; status = "Active";
            if (notify) mc.execute(() -> ChatUtils.infoPrefix("Chalk License", "Account verified. License active."));
        } catch (Exception error) {
            leaseUntil = 0; tokenUntil = 0; status = expired ? "Expired" : "Locked / Offline";
            if (notify) mc.execute(() -> ChatUtils.errorPrefix("Chalk License", "Verification failed or license expired. Check your code, Minecraft login and Internet connection."));
        } finally { busy = false; }
    }

    private static JsonObject post(String path, JsonObject body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.body().length() > 8192) throw new IllegalStateException();
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        if (response.statusCode() != 200) {
            expired = result.has("expired") && result.get("expired").getAsBoolean();
            throw new IllegalStateException();
        }
        return result;
    }

    public static boolean usable() {
        return account != null && account.equals(Minecraft.getInstance().getUser().getProfileId()) && System.nanoTime() < leaseUntil;
    }
    public static boolean expired() { return expired || (trialUntil > 0 && System.nanoTime() >= trialUntil); }
    public static long remaining() { return trialUntil == Long.MAX_VALUE ? -1 : Math.max(0, TimeUnit.NANOSECONDS.toMillis(trialUntil - System.nanoTime())); }
    public static String status() { return status; }
}
