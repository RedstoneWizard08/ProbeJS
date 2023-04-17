package com.probejs.compiler;

import com.probejs.ProbeCommands;
import com.probejs.formatter.formatter.*;
import com.probejs.formatter.formatter.special.FormatterIngredient;
import com.probejs.formatter.formatter.special.FormatterLootTable;
import com.probejs.formatter.formatter.special.FormatterMod;
import com.probejs.formatter.formatter.special.FormatterRecipeId;
import com.probejs.formatter.formatter.special.FormatterRegistry;
import com.probejs.formatter.formatter.special.FormatterTag;
import com.probejs.util.PlatformSpecial;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SpecialCompiler {
    public static final List<IFormatter> specialCompilers = new ArrayList<>();

    public static String rl2Cap(ResourceLocation location) {
        String[] elements = location.getPath().split("/");
        return Arrays.stream(elements[elements.length - 1].split("_"))
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                .collect(Collectors.joining(""));
    }

    private static List<FormatterTag> getTagFormatters() {
        List<FormatterTag> formatters = new ArrayList<>();
        ProbeCommands.COMMAND_LEVEL.registryAccess().registries().forEach(entry -> {
            ResourceKey<?> key = entry.key();
            Registry<?> registry = entry.value();
            formatters.add(new FormatterTag(rl2Cap(key.location()) + "Tag", registry));
        });
        return formatters;
    }

    public static List<IFormatter> compileSpecial() {
        List<IFormatter> formatters = new ArrayList<>();
        formatters.add(new FormatterMod());
        formatters.add(new FormatterIngredient());
        formatters.add(new FormatterRecipeId());
        formatters.add(new FormatterLootTable());
        formatters.addAll(getTagFormatters());
        formatters.addAll(PlatformSpecial.INSTANCE.get().getPlatformFormatters());
        formatters.addAll(specialCompilers);
        return formatters;
    }


    public static List<String> compileTagEvents() {
        return List.of();
    }
}
