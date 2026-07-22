package space.blockera.client.ui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import space.blockera.client.account.LauncherAccountBridge;

/** Laby-style account chooser backed by the launcher's token-safe account store. */
public final class BlockeraAccountScreen extends Screen {
    private static final int PAGE_SIZE = 5;

    private final Screen parent;
    private List<LauncherAccountBridge.Account> accounts = List.of();
    private Component status = Component.translatable("blockera.accounts.loading");
    private boolean loading = true;
    private boolean switching;
    private int page;

    public BlockeraAccountScreen(Screen parent) {
        super(Component.translatable("blockera.accounts.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildControls();
        if (loading) {
            loadAccounts();
        }
    }

    private void rebuildControls() {
        clearWidgets();
        int panelWidth = Math.min(360, width - 32);
        int left = (width - panelWidth) / 2;
        int top = Math.max(82, height / 2 - 115);
        int rowY = top + 50;
        int start = page * PAGE_SIZE;
        int end = Math.min(accounts.size(), start + PAGE_SIZE);
        for (LauncherAccountBridge.Account account : accounts.subList(start, end)) {
            Component label = Component.literal(
                (account.active() ? "✓  " : "")
                    + account.username() + "  ·  " + readableType(account.accountType()));
            BlockeraButton button = new BlockeraButton(
                left + 18,
                rowY,
                panelWidth - 36,
                ThemeTokens.CONTROL_HEIGHT,
                label,
                ignored -> switchTo(account),
                account.active()
            );
            button.active = !switching && !account.active();
            addRenderableWidget(button);
            rowY += 28;
        }

        int footerY = top + 194;
        if (accounts.size() > PAGE_SIZE) {
            BlockeraButton previous = new BlockeraButton(left + 18, footerY, 46,
                ThemeTokens.CONTROL_HEIGHT, Component.literal("‹"), ignored -> {
                    page--;
                    rebuildControls();
                });
            previous.active = !switching && page > 0;
            addRenderableWidget(previous);
            int pages = (accounts.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            BlockeraButton next = new BlockeraButton(left + panelWidth - 64, footerY, 46,
                ThemeTokens.CONTROL_HEIGHT, Component.literal("›"), ignored -> {
                    page++;
                    rebuildControls();
                });
            next.active = !switching && page + 1 < pages;
            addRenderableWidget(next);
        }

        BlockeraButton back = new BlockeraButton(
            left + 18,
            top + 222,
            panelWidth - 36,
            ThemeTokens.CONTROL_HEIGHT,
            Component.translatable("gui.back"),
            ignored -> onClose()
        );
        back.active = !switching;
        addRenderableWidget(back);
    }

    private void loadAccounts() {
        loading = false;
        LauncherAccountBridge bridge = LauncherAccountBridge.get();
        if (!bridge.isAvailable()) {
            status = Component.translatable("blockera.accounts.unavailable");
            return;
        }
        bridge.listAccounts().whenComplete((loaded, error) -> minecraft.execute(() -> {
            if (error != null) {
                status = Component.translatable("blockera.accounts.error");
                return;
            }
            accounts = loaded;
            status = accounts.isEmpty()
                ? Component.translatable("blockera.accounts.empty")
                : Component.translatable("blockera.accounts.choose");
            rebuildControls();
        }));
    }

    private void switchTo(LauncherAccountBridge.Account account) {
        if (switching || account.active()) {
            return;
        }
        switching = true;
        status = Component.translatable("blockera.accounts.switching", account.username());
        rebuildControls();
        LauncherAccountBridge.get().switchAccount(account.uuid())
            .whenComplete((ignored, error) -> minecraft.execute(() -> {
                if (error != null) {
                    switching = false;
                    status = Component.translatable("blockera.accounts.error");
                    rebuildControls();
                    return;
                }
                status = Component.translatable("blockera.accounts.restarting", account.username());
                minecraft.stop();
            }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanorama(graphics, partialTick);
        graphics.fill(0, 0, width, height, ThemeTokens.BACKDROP);
        int panelWidth = Math.min(360, width - 32);
        int left = (width - panelWidth) / 2;
        int top = Math.max(82, height / 2 - 115);
        BlockeraDraw.roundedRect(graphics, left, top, left + panelWidth, top + 260,
            ThemeTokens.RADIUS, ThemeTokens.PANEL);
        UiText.drawCentered(graphics, title, width / 2, top + 16, ThemeTokens.TEXT);
        UiText.drawCentered(graphics, status, width / 2, top + 32,
            switching ? ThemeTokens.WARNING : ThemeTokens.MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
        UiText.drawCentered(graphics, Component.translatable("blockera.accounts.security"),
            width / 2, top + 250, ThemeTokens.DIM);
    }

    @Override
    public void onClose() {
        if (!switching) {
            minecraft.setScreen(parent);
        }
    }

    private static String readableType(String accountType) {
        return switch (accountType.toLowerCase()) {
            case "microsoft" -> "Microsoft";
            case "elyby" -> "Ely.by";
            case "pirate" -> "Offline";
            default -> accountType;
        };
    }
}
