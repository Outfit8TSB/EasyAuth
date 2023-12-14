package xyz.nikitacartes.easyauth.config;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static xyz.nikitacartes.easyauth.EasyAuth.gameDirectory;
import static xyz.nikitacartes.easyauth.utils.EasyLogger.LogError;

public abstract class ConfigTemplate {
    private transient final Pattern pattern = Pattern.compile("^[^$\"{}\\[\\]:=,+#`^?!@*&\\\\\\s/]+");
    transient final String configPath;

    ConfigTemplate(String configPath) {
        this.configPath = configPath;
    }

    public static <Config extends ConfigTemplate> Config loadConfig(Class<Config> configClass, String configPath) {
        Path path = gameDirectory.resolve("config/EasyAuth").resolve(configPath);
        if (Files.exists(path)) {
            final HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                    .defaultOptions(configurationOptions ->
                            configurationOptions.serializers(builder ->
                                    builder.register(MutableText.class, MutableTextSerializer.INSTANCE)))
                    .path(path).build();
            try {
                return loader.load().get(configClass);
            } catch (ConfigurateException e) {
                throw new RuntimeException("[EasyAuth] Failed to load config file", e);
            }
        } else {
            return null;
        }
    }

    public void save() {
        Path path = gameDirectory.resolve("config/EasyAuth/" + configPath);
        try {
            Files.writeString(path, handleTemplate());
        } catch (IOException e) {
            LogError("Failed to save config file", e);
        }
    }

    private String escapeString(String string) {
        return string
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\"", "\\\"")
                .replace("'", "\\'");
    }

    protected <T> String wrapIfNecessary(T string) {
        String escapeString = escapeString(String.valueOf(string));
        if (!pattern.matcher(escapeString).matches()) {
            return "\"" + escapeString + "\"";
        } else {
            return escapeString;
        }
    }

    protected String wrapIfNecessary(double string) {
        return String.format(Locale.US, "%.4f", string);
    }

    protected String wrapIfNecessary(long string) {
        return String.valueOf(string);
    }

    protected <T extends List<String>> String wrapIfNecessary(T strings) {
        return "[" + strings
                .stream()
                .map(this::wrapIfNecessary)
                .collect(Collectors.joining(",\n  ")) + "]";
    }

    protected String wrapIfNecessary(MutableText text) {
        if (text.getContent() instanceof TranslatableTextContent) {
            return wrapIfNecessary(((TranslatableTextContent) text.getContent()).getKey());
        } else {
            return wrapIfNecessary(text.getString());
        }
    }

    protected abstract String handleTemplate() throws IOException;


    static final class MutableTextSerializer implements TypeSerializer<MutableText> {
        static final MutableTextSerializer INSTANCE = new MutableTextSerializer();
        @Override
        public MutableText deserialize(Type type, ConfigurationNode node) {
            return Text.translatableWithFallback("text.easyauth." + node.key(), node.getString());
        }

        @Override
        public void serialize(Type type, @Nullable MutableText obj, ConfigurationNode node) throws SerializationException {
            if (obj == null || obj.getString().isEmpty()) {
                node.raw(null);
                return;
            }
            node.set(obj.getString());
        }
    }

}
