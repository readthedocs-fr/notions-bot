package tech.hokkaydo.notionsbot;

import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
import tech.hokkaydo.notionsbot.command.manager.CommandManager;

/**
 * Created by Hokkaydo on 17-08-2020.
 */
public class Main {

    public static void main(String[] args) {

        final String token = args[0];
        final DiscordClient client = DiscordClient.create(token);
        final GatewayDiscordClient gateway = client.login().block();
        assert gateway != null;
        final CommandManager commandManager = new CommandManager();
        commandManager.setCommands(gateway);

        gateway.updatePresence(Presence.online(Activity.watching("Type " + CommandManager.PREFIX + "notions <tags> to find a sheet"))).block();
        gateway.on(MessageCreateEvent.class).subscribe(event -> commandManager.processCommand(event).subscribe());
        gateway.onDisconnect().block();
    }
}
