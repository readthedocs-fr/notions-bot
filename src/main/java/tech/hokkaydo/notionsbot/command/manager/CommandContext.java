package tech.hokkaydo.notionsbot.command.manager;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.MessageChannel;

import java.util.List;

/**
 * Created by Hokkaydo on 31-08-2020.
 */
public class CommandContext {

    private final String commandName;
    private final List<String> args;
    private final String messageContent;
    private final List<String> splitMessageContent;

    private final Member author;
    private final Guild guild;
    private final Snowflake guildId;
    private final MessageChannel channel;

    public CommandContext(String commandName, List<String> args, String messageContent, List<String> splitMessageContent, Member author, Guild guild, Snowflake guildId, MessageChannel channel) {
        this.commandName = commandName;
        this.args = args;
        this.messageContent = messageContent;
        this.splitMessageContent = splitMessageContent;
        this.author = author;
        this.guild = guild;
        this.guildId = guildId;
        this.channel = channel;
    }

    public String getCommandName() {
        return commandName;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public List<String> getSplitMessageContent() {
        return splitMessageContent;
    }

    public Member getAuthor() {
        return author;
    }

    public Guild getGuild() {
        return guild;
    }

    public Snowflake getGuildId() {
        return guildId;
    }

    public MessageChannel getChannel() {
        return channel;
    }

    public List<String> getArgs() {
        return args;
    }
}
