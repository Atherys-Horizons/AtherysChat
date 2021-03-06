package com.atherys.chat.facade;

import com.atherys.chat.AtherysChat;
import com.atherys.chat.config.AtherysChatConfig;
import com.atherys.chat.exception.AtherysChatException;
import com.atherys.chat.model.AtherysChannel;
import com.atherys.chat.service.ChatService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.message.MessageChannelEvent;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.chat.ChatTypes;
import org.spongepowered.api.text.format.TextColors;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ChannelFacade {

    @Inject
    private AtherysChatConfig chatConfig;

    @Inject
    private ChatService chatService;

    @Inject
    private ChatMessagingFacade cmf;

    public ChannelFacade() {
    }

    public void onPlayerJoin(Player player) {
        chatService.setDefaultChannels(player);
    }

    public void onPlayerChat(MessageChannelEvent.Chat event, Player player) {
        event.setCancelled(true);

        AtherysChannel channel = chatService.getPlayerSpeakingChannel(player);
        if (!chatService.hasWritePermission(player, channel)) {
            Text message = cmf.formatError("You do not have permission to talk in the ", channel.getTextName(), " channel.");
            player.sendMessage(message);
            return;
        }
        channel.send(player, event.getOriginalMessage(), ChatTypes.CHAT);
    }

    public Set<AtherysChannel> getPlayerVisibleChannels(Player player) {
        return chatService.getChannels().values().stream()
                .filter(channel -> channel.getPermission() == null || player.hasPermission(channel.getPermission()))
                .collect(Collectors.toSet());
    }

    public Set<AtherysChannel> getPlayerMemberChannels(Player player) {
        return chatService.getChannels().values().stream()
                .filter(channel -> channel.getPlayers().contains(player.getUniqueId()))
                .collect(Collectors.toSet());
    }

    public Set<AtherysChannel> getPlayerNonMemberChannels(Player player) {
        Set<AtherysChannel> nonMemberChannels = new HashSet<>(getPlayerVisibleChannels(player));
        nonMemberChannels.removeAll(getPlayerMemberChannels(player));
        return nonMemberChannels;
    }

    public void joinChannel(Player source, AtherysChannel channel) throws CommandException {
        if (!chatService.hasReadPermission(source, channel)){
            throw new AtherysChatException("You do not have permission to join the ", channel.getTextName(), " channel.");
        }
        addPlayerToChannel(source, channel);
    }

    public void leaveChannel(Player source, AtherysChannel channel) throws CommandException {
        if (!chatService.hasLeavePermission(source, channel)) {
            throw new AtherysChatException("You do not have permission to leave the ", channel.getTextName(), " channel.");
        }
        if (channel.getPlayers().contains(source.getUniqueId())) {
            removePlayerFromChannel(source, channel);
        } else {
            throw new AtherysChatException("You are not in that channel.");
        }

        cmf.info(source, "You have left ", channel.getTextName(), ".");
    }

    public void removePlayerFromChannel(Player player, AtherysChannel channel) {
        chatService.removePlayerFromChannel(player, channel);

        // If this is the players speaking channel, set it to another channel they are in
        if (channel == chatService.getPlayerSpeakingChannel(player)) {
            chatService.setPlayerSpeakingChannel(player, chatService.getPlayerChannel(player));
        }
    }

    public void addPlayerToChannel(Player player, AtherysChannel channel) {
        chatService.addPlayerToChannel(player, channel);
        chatService.setPlayerSpeakingChannel(player, channel);
        cmf.info(player, "You are now chatting in ", channel.getTextName(), ".");
    }

    public void speakToChannel(Player player, AtherysChannel channel, String message) throws CommandException {
        if (!chatService.hasWritePermission(player, channel)) {
            throw new AtherysChatException("You do not have permission to talk in the ", channel.getTextName(), " channel.");
        }
        if (!channel.getPlayers().contains(player.getUniqueId())) {
            cmf.info(player, "You have joined ", channel.getTextName(), ".");
            chatService.addPlayerToChannel(player, channel);
        }
        channel.send(player, Text.of(message));
    }

    public void displayPlayerChannels(Player player) {
        Text.Builder builder;

        // List the channel they are currently speaking in
        builder = Text.builder()
                .append(Text.of(TextColors.DARK_GREEN, "Currently speaking in: "))
                .append(chatService.getPlayerSpeakingChannel(player).getTextName());
        player.sendMessage(builder.build());

        // List the currently joined in channels
        builder = Text.builder()
                .append(Text.of(TextColors.DARK_GREEN, "Joined channels: "))
                .append(Text.joinWith(Text.of(", "), getPlayerMemberChannels(player).stream()
                        .map(AtherysChannel::getTextName).collect(Collectors.toSet())));
        player.sendMessage(builder.build());

        // List the available channels
        builder = Text.builder()
                .append(Text.of(TextColors.DARK_GREEN, "Available channels: "))
                .append(Text.joinWith(Text.of(", "), getPlayerNonMemberChannels(player).stream()
                        .map(AtherysChannel::getTextName).collect(Collectors.toSet())));
        player.sendMessage(builder.build());
    }
}

