package net.draycia.carbon.managers;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import net.draycia.carbon.CarbonChat;
import net.draycia.carbon.storage.ChatUser;
import net.draycia.carbon.util.RedisListener;
import net.kyori.adventure.text.format.TextColor;

import java.util.UUID;

public class RedisManager {

    private final CarbonChat carbonChat;

    private final RedisClient client;

    private final StatefulRedisPubSubConnection<String, String> subscribeConnection;
    private final RedisPubSubCommands<String, String> subscribeSync;

    private final StatefulRedisPubSubConnection<String, String> publishConnection;
    private final RedisPubSubCommands<String, String> publishSync;

    private final UUID serverUUID = UUID.randomUUID();

    public RedisManager(CarbonChat carbonChat) {
        this.carbonChat = carbonChat;

        String host = carbonChat.getConfig().getString("redis.host");
        String password = carbonChat.getConfig().getString("redis.password");
        int port = carbonChat.getConfig().getInt("redis.port");
        int database = carbonChat.getConfig().getInt("redis.database");

        RedisURI.Builder builder = RedisURI.Builder.redis(host, port)
                .withDatabase(database);

        if (password != null) {
            builder.withPassword(password.toCharArray());
        }

        this.client = RedisClient.create(builder.build());

        this.subscribeConnection = client.connectPubSub();
        this.subscribeSync = subscribeConnection.sync();

        this.publishConnection = client.connectPubSub();
        this.publishSync = publishConnection.sync();

        subscribeConnection.addListener((RedisListener)(channel, message) -> {
            String[] messagePieces = message.split(":");

            UUID messageUUID = UUID.fromString(messagePieces[0]);

            if (messageUUID.equals(serverUUID)) {
                return;
            }

            UUID uuid = UUID.fromString(messagePieces[1]);

            ChatUser user = carbonChat.getUserService().wrapIfLoaded(uuid);

            if (user == null) {
                return;
            }

            String value = messagePieces[2];

            switch (channel.toLowerCase()) {
                case "nickname":
                    handleNicknameChange(user, value);
                    break;
                case "nickname-reset":
                    handleNicknameResetChange(user, value);
                    break;
                case "selected-channel":
                    handleSelectedChannelChange(user, value);
                    break;
                case "spying-whispers":
                    handleSpyingWhispersChange(user, value);
                    break;
                case "muted":
                    handleMutedChange(user, value);
                    break;
                case "shadow-muted":
                    handleShadowMutedChange(user, value);
                    break;
                case "reply-target":
                    handleReplyTargetChange(user, value);
                    break;
                case "ignoring-user":
                    handleIgnoringUserChange(user, value, true);
                    break;
                case "unignoring-user":
                    handleIgnoringUserChange(user, value, false);
                    break;
                case "ignoring-channel":
                    handleIgnoringChannelChange(user, value, true);
                    break;
                case "unignoring-channel":
                    handleIgnoringChannelChange(user, value, false);
                    break;
                case "spying-channel":
                    handleSpyingChannelChange(user, value, true);
                    break;
                case "unspying-channel":
                    handleSpyingChannelChange(user, value, false);
                    break;
                case "channel-color":
                    handleChannelColorChange(user, value);
                    break;
            }
        });

        subscribeSync.subscribe("nickname", "selected-channel", "spying-whispers", "muted", "shadow-muted",
                "reply-target", "ignoring-user", "unignoring-user", "ignoring-channel", "unignoring-channel",
                "spying-channel", "unspying-channel", "channel-color");
    }

    private void handleChannelColorChange(ChatUser user, String value) {
        user.getChannelSettings(carbonChat.getChannelManager().getRegistry().get(value)).setColor(TextColor.fromHexString(value), true);
    }

    private void handleSpyingChannelChange(ChatUser user, String value, boolean b) {
        user.getChannelSettings(carbonChat.getChannelManager().getRegistry().get(value)).setSpying(b, true);
    }

    private void handleIgnoringChannelChange(ChatUser user, String value, boolean b) {
        user.getChannelSettings(carbonChat.getChannelManager().getRegistry().get(value)).setIgnoring(b, true);
    }

    private void handleIgnoringUserChange(ChatUser user, String value, boolean b) {
        user.setIgnoringUser(UUID.fromString(value), b, true);
    }

    private void handleReplyTargetChange(ChatUser user, String value) {
        user.setReplyTarget(UUID.fromString(value), true);
    }

    private void handleShadowMutedChange(ChatUser user, String value) {
        user.setShadowMuted(value.equalsIgnoreCase("true"), true);
    }

    private void handleMutedChange(ChatUser user, String value) {
        user.setMuted(value.equalsIgnoreCase("true"), true);
    }

    private void handleSpyingWhispersChange(ChatUser user, String value) {
        user.setSpyingWhispers(value.equalsIgnoreCase("true"), true);
    }

    private void handleSelectedChannelChange(ChatUser user, String value) {
        user.setSelectedChannel(carbonChat.getChannelManager().getRegistry().get(value), true);
    }

    private void handleNicknameResetChange(ChatUser user, String value) {
        user.setNickname(null, true);

        if (user.isOnline()) {
            String message = carbonChat.getConfig().getString("language.nickname-reset");

            user.sendMessage(carbonChat.getAdventureManager().processMessageWithPapi(user.asPlayer(),
                    message, "nickname", ""));
        }
    }

    private void handleNicknameChange(ChatUser user, String value) {
        user.setNickname(value, true);

        if (user.isOnline()) {
            String message = carbonChat.getConfig().getString("language.nickname-set");

            user.sendMessage(carbonChat.getAdventureManager().processMessageWithPapi(user.asPlayer(),
                    message, "nickname", value));
        }
    }

    public void publishChange(UUID uuid, String field, String value) {
        publishSync.publish(field, serverUUID + ":" + uuid.toString() + ":" + value);
    }
}
