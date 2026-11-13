package dev.skyprofile.mixin;

import dev.skyprofile.SkyProfileClient;
import dev.skyprofile.chat.SocialOptionsHook;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles clicks on our own [View Profile] button locally, so "/skyprofile <name>" is never
 * sent to the server, and remembers which name the player clicked for social options.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
	private void skyprofile$handleClick(Style clicked, boolean allowInsertions, CallbackInfoReturnable<Boolean> cir) {
		if (allowInsertions || clicked == null) return;
		SocialOptionsHook.onChatComponentClicked(clicked);
		if (clicked.getClickEvent() instanceof ClickEvent.RunCommand(String command)) {
			String c = command.startsWith("/") ? command.substring(1) : command;
			if (c.startsWith("skyprofile ")) {
				SkyProfileClient.openProfileNow(c.substring("skyprofile ".length()).trim());
				cir.setReturnValue(true);
			}
		}
	}
}
