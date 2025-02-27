/*
 * CarbonChat
 *
 * Copyright (c) 2024 Josua Parks (Vicarious)
 *                    Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.draycia.carbon.common.command.commands;

import com.google.inject.Inject;
import net.draycia.carbon.api.users.CarbonPlayer;
import net.draycia.carbon.common.command.CarbonCommand;
import net.draycia.carbon.common.command.CommandSettings;
import net.draycia.carbon.common.command.Commander;
import net.draycia.carbon.common.command.ParserFactory;
import net.draycia.carbon.common.command.PlayerCommander;
import net.draycia.carbon.common.config.ConfigManager;
import net.draycia.carbon.common.messages.CarbonMessages;
import net.draycia.carbon.common.messages.TagPermissions;
import net.draycia.carbon.common.util.CloudUtils;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.incendo.cloud.CommandManager;

import static org.incendo.cloud.minecraft.extras.RichDescription.richDescription;
import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;

@DefaultQualifier(NonNull.class)
public final class NicknameCommand extends CarbonCommand {

    private final CommandManager<Commander> commandManager;
    private final CarbonMessages carbonMessages;
    private final ParserFactory parserFactory;
    private final ConfigManager config;

    @Inject
    public NicknameCommand(
        final CommandManager<Commander> commandManager,
        final CarbonMessages carbonMessages,
        final ParserFactory parserFactory,
        final ConfigManager config
    ) {
        this.commandManager = commandManager;
        this.carbonMessages = carbonMessages;
        this.parserFactory = parserFactory;
        this.config = config;
    }

    @Override
    public CommandSettings defaultCommandSettings() {
        return new CommandSettings("nickname", "nick");
    }

    @Override
    public Key key() {
        return Key.key("carbon", "nickname");
    }

    @Override
    public void init() {
        if (!this.config.primaryConfig().nickname().useCarbonNicknames()) {
            return;
        }

        // TODO: Allow UUID input for target player
        final var selfRoot = this.commandManager.commandBuilder(this.commandSettings().name(), this.commandSettings().aliases());
        final var othersRoot = selfRoot.literal("player")
            .required("player", this.parserFactory.carbonPlayer(), richDescription(this.carbonMessages.commandNicknameArgumentPlayer()));

        // Check nickname
        this.commandManager.command(selfRoot.permission("carbon.nickname")
            .commandDescription(richDescription(this.carbonMessages.commandNicknameDescription()))
            .handler(ctx -> this.checkOwnNickname(CloudUtils.nonPlayerMustProvidePlayer(this.carbonMessages, ctx.sender()))));
        this.commandManager.command(othersRoot.permission("carbon.nickname.others")
            .commandDescription(richDescription(this.carbonMessages.commandNicknameOthersDescription()))
            .handler(ctx -> this.checkOthersNickname(ctx.sender(), ctx.get("player"))));

        // Set nickname
        this.commandManager.command(selfRoot.permission("carbon.nickname.set")
            .commandDescription(richDescription(this.carbonMessages.commandNicknameSetDescription()))
            .required("nickname", greedyStringParser(), richDescription(this.carbonMessages.commandNicknameArgumentNickname()))
            .handler(ctx -> this.applyNickname(ctx.sender(), CloudUtils.nonPlayerMustProvidePlayer(this.carbonMessages, ctx.sender()), ctx.get("nickname"))));
        this.commandManager.command(othersRoot.permission("carbon.nickname.others.set")
            .commandDescription(richDescription(this.carbonMessages.commandNicknameOthersSetDescription()))
            .required("nickname", greedyStringParser(), richDescription(this.carbonMessages.commandNicknameArgumentNickname()))
            .handler(ctx -> this.applyNickname(ctx.sender(), ctx.get("player"), ctx.get("nickname"))));

        // Reset/remove nickname
        this.commandManager.command(selfRoot.permission("carbon.nickname.set")
            .commandDescription(richDescription(this.carbonMessages.commandNicknameResetDescription()))
            .literal("reset")
            .handler(ctx -> this.resetNickname(ctx.sender(), CloudUtils.nonPlayerMustProvidePlayer(this.carbonMessages, ctx.sender()))));
        this.commandManager.command(othersRoot.permission("carbon.nickname.others.set")
            .commandDescription(richDescription(this.carbonMessages.commandNicknameOthersResetDescription()))
            .literal("reset")
            .handler(ctx -> this.resetNickname(ctx.sender(), ctx.get("player"))));
    }

    private void resetNickname(final Commander sender, final CarbonPlayer target) {
        target.nickname(null);

        if (sender instanceof PlayerCommander playerCommander
            && playerCommander.carbonPlayer().uuid().equals(target.uuid())) {
            this.carbonMessages.nicknameReset(target);
        } else {
            this.carbonMessages.nicknameResetOthers(sender, target.username());
        }
    }

    private void applyNickname(final Commander sender, final CarbonPlayer target, final String nick) {
        final Component parsedNick = parseNickname(sender, nick);

        final String plainNick = PlainTextComponentSerializer.plainText().serialize(parsedNick);

        // If the nickname is caught in the character limit, return without setting a nickname.
        final int minLength = this.config.primaryConfig().nickname().minLength();
        final int maxLength = this.config.primaryConfig().nickname().maxLength();
        if (plainNick.length() < minLength || maxLength < plainNick.length()) {
            this.carbonMessages.nicknameErrorCharacterLimit(sender, parsedNick, minLength, maxLength);
            return;
        }

        if (this.config.primaryConfig().nickname().blackList().stream().anyMatch(plainNick::equalsIgnoreCase)) {
            this.carbonMessages.nicknameErrorBlackList(sender, parsedNick);
            return;
        }

        if (!sender.hasPermission("carbon.nickname.filter") && !plainNick.matches(this.config.primaryConfig().nickname().filter())) {
            this.carbonMessages.nicknameErrorFilter(sender, parsedNick);
            return;
        }

        target.nickname(parsedNick);

        if (sender instanceof PlayerCommander playerCommander
            && playerCommander.carbonPlayer().uuid().equals(target.uuid())) {
            // Setting own nickname
            this.carbonMessages.nicknameSet(sender, parsedNick);
        } else {
            // Setting other player's nickname
            this.carbonMessages.nicknameSet(target, parsedNick);
            this.carbonMessages.nicknameSetOthers(sender, target.username(), parsedNick);
        }
    }

    private void checkOwnNickname(final CarbonPlayer sender) {
        if (sender.nickname() != null) {
            this.carbonMessages.nicknameShow(sender, sender.username(), sender.nickname());
        } else {
            this.carbonMessages.nicknameShowUnset(sender, sender.username());
        }
    }

    private void checkOthersNickname(final Audience sender, final CarbonPlayer target) {
        if (target.nickname() != null) {
            this.carbonMessages.nicknameShowOthers(sender, target.username(), target.nickname());
        } else {
            this.carbonMessages.nicknameShowOthersUnset(sender, target.username());
        }
    }

    private static Component parseNickname(final Commander sender, final String nick) {
        // trim one level of quotes, to allow for nicknames which collide with command literals
        return TagPermissions.parseTags(TagPermissions.NICKNAME, trimQuotes(nick), sender::hasPermission);
    }

    private static String trimQuotes(final String string) {
        if (string.length() < 3) {
            return string;
        }
        final char first = string.charAt(0);
        if ((first == '\'' || first == '"') && string.endsWith(String.valueOf(first))) {
            return string.substring(1, string.length() - 1);
        }
        return string;
    }

}
