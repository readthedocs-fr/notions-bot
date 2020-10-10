package tech.hokkaydo.notionsbot.command.manager;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.hokkaydo.notionsbot.command.NotionsCommand;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Hokkaydo on 30-08-2020.
 */
public class CommandManager {

    private final Map<String, Command> commands = Map.of("notions", new NotionsCommand());

    public static final String PREFIX = "?";

    private GatewayDiscordClient gateway = null;
    public void setCommands(GatewayDiscordClient gateway){
        this.gateway = gateway;
    }
    public Mono<Boolean> processCommand(MessageCreateEvent event){
        final Message message = event.getMessage();

        //Filter commands
        final Matcher prefixRegexMatcher = Pattern
                .compile("^(\\" + PREFIX + "\\s?|<@!?" + gateway.getSelf().block().getId().asString() + ">\\s?)(.*)")
                .matcher(message.getContent());
        if(prefixRegexMatcher.matches()){
            final String messageContent = message.getContent().replaceFirst(Pattern.quote(prefixRegexMatcher.group(1)), "");

            List<String> splitContent = Arrays.asList(messageContent.split(" "));

            Mono<CommandContext> commandContext = event.getGuild().flatMap(guild ->
                    event.getMessage().getChannel().flatMap(channel ->
                            Mono.just(
                                    new CommandContext(
                                            splitContent.get(0),
                                            splitContent.subList(1, splitContent.size()),
                                            messageContent,
                                            splitContent,
                                            event.getMember().orElse(null),
                                            guild,
                                            event.getGuildId().orElse(null),
                                            channel
                                    )
                            )
                    )
            );
            return commandContext.map(ctx ->
                    Flux.fromIterable(commands.entrySet())
                            .filter(cmd -> cmd.getKey().equalsIgnoreCase(ctx.getCommandName()))
                            .onErrorContinue(((throwable, o) -> {
                                System.out.println(o);
                                throwable.printStackTrace();
                            }))
                            .map(Map.Entry::getValue)
                            .doOnNext(cmd -> cmd.executeCommand(ctx))
            ).flatMap(Flux::count)
                    .map(Long::intValue)
                    .map(integer -> integer > 1);
        }
        return Mono.just(false);
    }
}
