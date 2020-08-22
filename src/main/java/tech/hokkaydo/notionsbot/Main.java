package tech.hokkaydo.notionsbot;

import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by Hokkaydo on 17-08-2020.
 */
public class Main {

    private static final String PREFIX = "?";

    public static void main(String[] args) {

        final String token = args[0];
        final DiscordClient client = DiscordClient.create(token);
        final GatewayDiscordClient gateway = client.login().block();

        gateway.updatePresence(Presence.online(Activity.watching("Type " + PREFIX + "notions <tags> to find a sheet"))).block();

        gateway.on(MessageCreateEvent.class).subscribe(event -> {
            final Message message = event.getMessage();

            //Filter commands
            final Matcher prefixRegexMatcher = Pattern
                    .compile("^(\\" + PREFIX + "\\s?|<@!?" + gateway.getSelf().block().getId().asString() + ">\\s?)(.*)")
                    .matcher(message.getContent());

            if(!prefixRegexMatcher.matches()) return;

            //Remove prefix
            final String messageContent = message.getContent().replaceFirst(Pattern.quote(prefixRegexMatcher.group(1)), "");

            List<String> splitContent = Arrays.asList(messageContent.split(" "));

            if (splitContent.get(0).equalsIgnoreCase("notions")) {
                final MessageChannel channel = message.getChannel().block();

                List<String> keywords = splitContent.subList(1, splitContent.size());

                getNotion(keywords).ifPresentOrElse(notions ->
                                channel.createEmbed(embed -> {
                                    StringBuilder stringBuilder = new StringBuilder();
                                    AtomicInteger i = new AtomicInteger(1);

                                    if(notions.size() == 1){
                                        stringBuilder.append(notions.get(0).getValue());
                                    }else {
                                        notions.forEach(s -> stringBuilder
                                                .append(i.getAndIncrement())
                                                .append(". ")
                                                .append(s.getKey())
                                                .append(" - ")
                                                .append(s.getValue())
                                                .append("\n\n")

                                        );
                                    }

                                    embed.setColor(Color.GREEN).addField(
                                            "Notion - " + notions.get(0).getKey(),
                                            (notions.size() > 1 ? "Notions trouvées pour les mots `" + getAsString(keywords) + "` : \n\n" : "")
                                                    + new String(stringBuilder.toString().getBytes(), StandardCharsets.UTF_8),
                                            true
                                    );
                                }).block(),

                        () -> channel.createEmbed(embed -> embed.setColor(Color.RED).addField(
                                "Notion not found",
                                "Aucune notion trouvée pour les mots `" + getAsString(keywords) + "`",
                                true
                                )

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

    private static Optional<List<Map.Entry<String, String>>> getNotion(List<String> keywords)  {
        String url = "https://api.github.com/repos/readthedocs-fr/notions/git/trees/master?recursive=true";

        try {
            URLConnection connection = new URL(url).openConnection();
            connection.setRequestProperty("accept", "application/vnd.github.v3+json");
            InputStream response = connection.getInputStream();


            try (Scanner scanner = new Scanner(response)) {
                String responseBody = scanner.useDelimiter("\\A").next();

                Map<Integer, Integer> pathScoreMap = new HashMap<>();
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(responseBody);
                JSONArray tree = (JSONArray) jsonObject.get("tree");
                for (int i = 0; i < tree.size(); i++) {
                    JSONObject object = (JSONObject) tree.get(i);
                    String path = (String) object.get("path");

                    //Filtering .md files
                    if(!path.split("/")[path.split("/").length - 1].endsWith(".md")) continue;

                    //Avoid README files
                    if(path.split("/")[path.split("/").length - 1].equalsIgnoreCase("README.md")) continue;

                    String[] duplicatedPathWords = path.toLowerCase().replaceAll(".md", "").split("([_/])");
                    String [] pathWords = Arrays.stream(duplicatedPathWords).collect(Collectors.groupingBy(Function.identity())).keySet().toArray(new String[0]);

                    int score = 0;
                    for(String keyword : keywords){
                        for(String pathWord : pathWords){
                            if(StringUtils.stripAccents(keyword).equalsIgnoreCase(StringUtils.stripAccents(pathWord))) score++;
                        }
                    }
                    if(score > 0) {
                        pathScoreMap.put(i, score);
                    }
                }
                if(pathScoreMap.size() == 0){
                    return Optional.empty();
                }

                pathScoreMap = sortByValue(pathScoreMap);

                List<Integer> indexList = new ArrayList<>(pathScoreMap.keySet());
                int maxScore = pathScoreMap.get(indexList.get(0));
                List<Integer> maxScoredIndexes = new ArrayList<>();

                //Filter max 9 entries because Discord doesn't support more than 30 lines in one embed
                for (int i = 0; i < indexList.size() && i < 9; i++) {
                    if(pathScoreMap.get(indexList.get(i)) == maxScore) maxScoredIndexes.add(indexList.get(i));
                }

                List<Map.Entry<String, String>> maxScoredNotions = new ArrayList<>();

                for (Integer maxScoredIndex : maxScoredIndexes) {

                    String path = (String) ((JSONObject)tree.get(maxScoredIndex)).get("path");

                    maxScoredNotions.add(new Map.Entry<>() {
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
                return Optional.of(maxScoredNotions);
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
        Collections.reverse(list);

        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;

    }
}
