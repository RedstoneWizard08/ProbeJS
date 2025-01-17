package com.probejs.compiler;

import com.mojang.datafixers.util.Pair;
import com.probejs.ProbeJS;
import com.probejs.ProbePaths;
import com.probejs.info.ClassInfo;
import com.probejs.jdoc.Serde;
import com.probejs.jdoc.document.DocumentClass;
import com.probejs.jdoc.property.AbstractProperty;
import com.probejs.jdoc.property.PropertyComment;
import com.probejs.jdoc.property.PropertyExtra;
import com.probejs.jdoc.property.PropertyType;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EventCompiler {
    public static Map<Pair<String, String>, Function<EventHandler, List<String>>> SPECIAL_EVENT_OVERRIDE = new HashMap<>();

    public static void initSpecialEvents() {
        SPECIAL_EVENT_OVERRIDE.put(new Pair<>("StartupEvents", "registry"), handler -> RegistryCompiler.compileRegistryEvents());
        //TODO: Get this done
        //SPECIAL_EVENT_OVERRIDE.put(new Pair<>("ServerEvents", "tags"), handler -> SpecialCompiler.compileTagEvents());
    }

    public static List<Class<?>> fetchEventClasses() {
        return EventGroup.getGroups().values().stream().map(EventGroup::getHandlers).map(Map::values).flatMap(Collection::stream).map(handler -> handler.eventType.get()).collect(Collectors.toList());
    }

    private static <T extends AbstractProperty<T>> Optional<T> findProperty(Map<String, DocumentClass> globalClasses, DocumentClass documentClass, Class<T> propertyClass) {
        var result = documentClass.findProperty(propertyClass);
        if (result.isPresent()) return result;
        var parent = PropertyType.getClazzName(documentClass.getParent()).orElse(null);
        if (parent != null && globalClasses.containsKey(parent)) {
            return findProperty(globalClasses, globalClasses.get(parent), propertyClass);
        }

        for (PropertyType<?> type : documentClass.getInterfaces()) {
            String implemented = PropertyType.getClazzName(type).orElse(null);
            if (implemented != null && globalClasses.containsKey(implemented)) {
                Optional<T> found = findProperty(globalClasses, globalClasses.get(implemented), propertyClass);
                if (found.isPresent()) return found;
            }
        }

        return Optional.empty();
    }

    public static void compileEvents(Map<String, DocumentClass> globalClasses) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(ProbePaths.GENERATED.resolve("events.d.ts"));
        writer.write("/// <reference path=\"./globals.d.ts\" />\n");
        writer.write("/// <reference path=\"./registries.d.ts\" />\n");

        for (Map.Entry<String, EventGroup> entry : EventGroup.getGroups().entrySet()) {
            String name = entry.getKey();
            EventGroup group = entry.getValue();

            List<String> elements = new ArrayList<>();
            elements.add("{");
            for (Map.Entry<String, EventHandler> e : group.getHandlers().entrySet()) {

                String eventName = e.getKey();
                EventHandler handler = e.getValue();
                Class<?> event = handler.eventType.get();
                ClassInfo eventType = ClassInfo.getOrCache(event);

                Function<EventHandler, List<String>> specialHandler = SPECIAL_EVENT_OVERRIDE.get(new Pair<>(name, eventName));

                if (specialHandler != null) {
                    elements.addAll(specialHandler.apply(handler));
                    continue; //Overrides default event formatting
                }
                DocumentClass document = globalClasses.get(eventType.getName());
                PropertyComment comment = document.getMergedComment()
                        .merge(new PropertyComment("This event does %s have results.".formatted(handler.getHasResult() ? "" : "**not** ")))
                        .merge(new PropertyComment("This event fires on **%s**.".formatted(
                                handler.scriptTypePredicate
                                        .getValidTypes()
                                        .stream()
                                        .map(t -> t.name)
                                        .collect(Collectors.joining(", "))
                        )));
                elements.addAll(comment.formatLines(4));
                if (handler.extra != null) {
                    elements.add("%s(extra: %s, handler: (event: %s) => void):void,".formatted(
                            eventName,
                            findProperty(globalClasses, document, PropertyExtra.class)
                                    .map(extra -> Serde.getTypeFormatter(extra.getType()).formatFirst())
                                    .orElse("string"), RegistryCompiler.formatMaybeParameterized(event)
                    ));
                }
                if (handler.extra == null || !handler.extra.required) {
                    elements.add("%s(handler: (event: %s) => void):void,".formatted(eventName, RegistryCompiler.formatMaybeParameterized(event)));
                }
            }
            elements.add("};\n");
            writer.write("declare const %s: %s".formatted(name, String.join("\n", elements)));
        }
        writer.close();
    }

}
