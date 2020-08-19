package tech.hokkaydo.notionsbot;

import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Color;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Created by Hokkaydo on 17-08-2020.
 */
public class Main {

    public static void main(String[] args) {
        final String token = args[0];
        final DiscordClient client = DiscordClient.create(token);
        final GatewayDiscordClient gateway = client.login().block();
        assert gateway != null;
        gateway.on(MessageCreateEvent.class).subscribe(event -> {
            final Message message = event.getMessage();
            if (message.getContent().startsWith("!notions")) {
                final MessageChannel channel = message.getChannel().block();
                assert channel != null;
                List<String> keywords = Arrays.asList(message.getContent().replace("!notions", "").split(" "));
                getNotion(keywords).ifPresentOrElse(
                        entry -> channel.createEmbed(embed ->
                                embed
                                        .setColor(Color.GREEN)
                                        .addField("Notion - " + entry.getKey(), "Notion trouvée pour les mots `" + getAsString(keywords) +  "` : \n" + new String(entry.getValue().getBytes(), StandardCharsets.UTF_8), true)
                        ).block(),
                        () -> channel.createEmbed(embed ->
                                embed
                                        .setColor(Color.RED)
                                        .addField("Notion not found", "Aucune notion trouvée pour les mots `" + getAsString(keywords) + "`", true)
                        ).block()
                );
            }
        });

        gateway.onDisconnect().block();
    }

    private static String getAsString(List<String> list){
        StringBuilder stringBuilder = new StringBuilder();
        list.forEach(s -> stringBuilder.append(s).append(" "));
        return stringBuilder.toString();
    }

    private static Optional<Map.Entry<String, String>> getNotion(List<String> keywords)  {
        String url = "https://api.github.com/repos/readthedocs-fr/notions/git/trees/3f6f6c7758296ecfeb64f6b33175022b8c7e642d?recursive=true";

        try {
            URLConnection connection = new URL(url).openConnection();
            connection.setRequestProperty("accept", "application/vnd.github.v3+json");
            InputStream response = connection.getInputStream();


            try (Scanner scanner = new Scanner(response)) {
                String responseBody = scanner.useDelimiter("\\A").next();
                System.out.println(responseBody);

                Map<Integer, Integer> pathScore = new HashMap<>();
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(responseBody);
                JSONArray tree = (JSONArray) jsonObject.get("tree");
                for (int i = 0; i < tree.size(); i++) {
                    JSONObject object = (JSONObject) tree.get(i);
                    String path = (String) object.get("path");
                    String[] pathWords = path.split("([._/])");
                    if(!path.split("/")[path.split("/").length - 1].endsWith(".md")) continue;
                    int score = 0;
                    for(String keyword : keywords){
                        for(String pathWord : pathWords){
                            if(StringUtils.stripAccents(keyword).equalsIgnoreCase(StringUtils.stripAccents(pathWord))) score++;
                        }
                    }
                    if(score > 0) {
                        pathScore.put(i, score);
                        System.out.println(i + " " + score + " " + path + " " + Arrays.toString(pathWords));
                    }
                }
                if(pathScore.size() == 0){
                    return Optional.empty();
                }
                pathScore = sortByValue(pathScore);
                int i = new ArrayList<>(pathScore.keySet()).get(new ArrayList<>(pathScore.keySet()).size() - 1);
                String path = (String) ((JSONObject)tree.get(i)).get("path");
                return Optional.of(new Map.Entry<>() {
                    @Override
                    public String getKey() {
                        return path.split("/")[path.split("/").length - 1];
                    }

                    @Override
                    public String getValue() {
                        return "https://github.com/readthedocs-fr/notions/blob/master/" + path;
                    }

                    @Override
                    public String setValue(String value) {
                        return null;
                    }
                });
            }
        }catch (IOException | ParseException ioe){
            ioe.printStackTrace();
        }
        return Optional.empty();
    }

    private static Map<Integer, Integer> sortByValue(Map<Integer, Integer> unsortMap) {

        List<Map.Entry<Integer, Integer>> list =
                new LinkedList<>(unsortMap.entrySet());

        list.sort(Map.Entry.comparingByValue());

        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;

    }
}
