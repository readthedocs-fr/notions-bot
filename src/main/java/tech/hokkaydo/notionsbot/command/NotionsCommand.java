package tech.hokkaydo.notionsbot.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Color;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import tech.hokkaydo.notionsbot.command.manager.Command;
import tech.hokkaydo.notionsbot.command.manager.CommandContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by Hokkaydo on 31-08-2020.
 */
public class NotionsCommand implements Command {

    final private static Pattern SPACE_SPLITTER = Pattern.compile(" ");
    final private static Pattern SLASH_SPLITTER = Pattern.compile("/");
    final private static Pattern PATH_SPLITTER = Pattern.compile("[/_]");

    public void executeCommand(CommandContext commandContext){
        List<String> keywords = commandContext.getArgs();

        getNotion(keywords).subscribe(notions -> {
            if(notions.isEmpty()){
                commandContext.getChannel().createEmbed(embed ->
                        embed.setColor(Color.RED).addField(
                                "Notion introuvable",
                                "Aucune notion trouvée pour les mots `" + getAsString(Objects.requireNonNull(keywords)) + "`",
                                false
                        )
                ).subscribe();
            }else{
                StringBuilder stringBuilder = new StringBuilder();

                AtomicInteger i = new AtomicInteger(1);

                AtomicBoolean firstSent = new AtomicBoolean(false);
                if(notions.size() == 1){
                    stringBuilder.append(notions.get(0).getT2());
                }else {
                    for (Tuple2<String, String> notion : notions) {
                        String name = getNotionNameFromPath(notion.getT1());
                        String line = i.getAndIncrement() + ". " + name + " - [" + notion.getT1().substring(0, notion.getT1().lastIndexOf("/")) + "](" + notion.getT2() + ")\n\n";
                        if (("Notion - " + (!firstSent.get() ? notions.get(0).getT1() : "Suite")).length() + stringBuilder.toString().length() + line.length() > 1024) {

                            sendEmbed(keywords, notions, commandContext.getChannel(), stringBuilder, firstSent);
                            firstSent.set(true);
                            stringBuilder.delete(0, stringBuilder.toString().length());
                        }

                        stringBuilder.append(line);
                    }
                }

                if(stringBuilder.toString().length() > 0) {
                    sendEmbed(keywords, notions, commandContext.getChannel(), stringBuilder, firstSent);
                }
            }
        });
    }

    @Override
    public String getName() {
        return "notions";
    }

    private void sendEmbed(List<String> keywords, List<Tuple2<String, String>> notions, MessageChannel channel, StringBuilder stringBuilder, AtomicBoolean firstSent) {
        channel.createEmbed(embed -> embed.setColor(Color.GREEN).addField(
                "Notion - " + (!firstSent.get() ? getNotionNameFromPath(notions.get(0).getT1()) : "Suite"),
                (!firstSent.get() ? (notions.size() > 1 ? "Notions trouvées pour les mots `" + getAsString(Objects.requireNonNull(keywords)) + "` : \n\n" : "") : "")
                        + stringBuilder.toString(),
                false
        )).subscribe();
    }
    private String capitalizeFirstLetters(String str) {
        str = str.toLowerCase();
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : SPACE_SPLITTER.split(str)) {
            if(Character.isLetter(s.charAt(0))) {
                stringBuilder.append(s.replaceFirst(String.valueOf(s.charAt(0)), String.valueOf(Character.toUpperCase(s.charAt(0)))));
            }else {
                stringBuilder.append(s);
            }
            stringBuilder.append(" ");
        }
        return stringBuilder.substring(0, stringBuilder.length() - 1);
    }

    private String getNotionNameFromPath(String path) {
        return capitalizeFirstLetters(
                SLASH_SPLITTER
                        .split(path)[SLASH_SPLITTER.split(path).length - 1]
                        .replace("_", " ")
        );
    }
  
    private Mono<List<Tuple2<String, String>>> getNotion(List<String> keywords)  {
        String url = "https://api.github.com/repos/readthedocs-fr/notions/git/trees/master?recursive=true";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest
                .newBuilder()
                .uri(URI.create(url))
                .build();
        return Mono.fromFuture(() -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString(Charset.defaultCharset())))
                .map(HttpResponse::body)
                .map(body -> new String(body.getBytes(), StandardCharsets.UTF_8))
                .flatMap(body -> Mono.fromCallable(() -> new JSONParser().parse(body)))
                .cast(JSONObject.class)
                .map(body -> body.get("tree"))
                .cast(JSONArray.class)
                .map(JSONAware::toJSONString)
                .flatMap(string -> Mono.fromCallable(() -> new ObjectMapper().readValue(string, new TypeReference<List<JSONObject>>() {}))
                .onErrorContinue((throwable, o) -> throwable.printStackTrace()))
                .flatMapIterable(list -> list)
                .map(obj -> obj.get("path"))
                .cast(String.class)
                .filter(this::filterNotions)
                .map(path -> Tuples.of(path, scorePath(path, keywords)))
                .filter(tuple -> tuple.getT2() > 0)
                .collectMap(Tuple2::getT1, Tuple2::getT2)
                .map(map -> {
                    map = sortByValue(map);
                    if(map.size() == 0) return new ArrayList<String>();
                    int maxScore = map.get(new ArrayList<>(map.keySet()).get(0));
                    List<String> keys = new ArrayList<>();
                    map.entrySet().stream().filter(entry -> entry.getValue() != maxScore).forEach(e -> keys.add(e.getKey()));
                    keys.forEach(map::remove);
                    return new ArrayList<>(map.keySet());
                })
                .map(values -> {
                    List<Tuple2<String, String>> notions = new ArrayList<>();
                    for (String path : values) {
                        notions.add(
                                Tuples.of(
                                        path.substring(0, path.length() - ".md".length()),
                                        "https://github.com/readthedocs-fr/notions/blob/master/" + path
                                )
                        );
                    }
                    return notions;
                });
    }

    private int scorePath(String path, List<String> keywords) {
        return Flux.just(PATH_SPLITTER.split(path.toLowerCase().replaceAll(".md", "")))
                .groupBy(Function.identity())
                .map(GroupedFlux::key)
                .filter(s -> this.containsStringIgnoreCase(keywords, s))
                .count()
                .block()
                .intValue();
    }

    private boolean containsStringIgnoreCase(List<String> list, String value){
        for (String s : list) {
            if(StringUtils.stripAccents(s).equalsIgnoreCase(StringUtils.stripAccents(value))) return true;
        }
        return false;
    }

    private Map<String, Integer> sortByValue(Map<String, Integer> unsortMap) {

        return unsortMap.entrySet()
                .stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
    }

    private String getAsString(List<?> list){
        StringBuilder stringBuilder = new StringBuilder();
        list.forEach(s -> stringBuilder.append(s).append(" "));
        return stringBuilder.substring(0, stringBuilder.toString().length() - 1);
    }
    private boolean filterNotions(String str) {
        String[] files = PATH_SPLITTER.split(str);
        String file = files[files.length - 1];
        return file.endsWith(".md") && !file.equalsIgnoreCase("README.md");
    }
}
