package co.casterlabs.rakurai.json.element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.json.serialization.JsonSerializationContext;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode
public class JsonArray implements JsonElement, Iterable<JsonElement> {
    private List<JsonElement> contents = new LinkedList<>();

    public JsonArray() {}

    public JsonArray(@NonNull Collection<? extends JsonElement> contents) {
        for (JsonElement e : contents) {
            this.add(e);
        }
    }

    public JsonArray(@NonNull JsonElement... contents) {
        for (JsonElement e : contents) {
            this.add(e);
        }
    }

    public JsonArray(@NonNull Number... contents) {
        for (Number n : contents) {
            this.add(n);
        }
    }

    public JsonArray(@NonNull String... contents) {
        for (String s : contents) {
            this.add(s);
        }
    }

    @Override
    public boolean isJsonArray() {
        return true;
    }

    @Override
    public JsonArray getAsArray() {
        return this;
    }

    /* Serialization */

    @Override
    public String toString() {
        return this.toString(false);
    }

    @Override
    public void serializeToArray(@NonNull JsonSerializationContext ctx) {
        ctx.startArray();

        for (JsonElement e : this.contents) {
            e.serializeToArray(ctx);
        }

        ctx.endArray();
    }

    @Override
    public void serializeToObject(@NonNull String key, @NonNull JsonSerializationContext ctx) {
        ctx.startArrayEntry(key);

        for (JsonElement e : this.contents) {
            e.serializeToArray(ctx);
        }

        ctx.endArray();
    }

    /* Helpers */

    public String getString(int index) {
        return this.contents.get(index).getAsString();
    }

    public JsonNumber getNumber(int index) {
        return this.contents.get(index).getAsNumber();
    }

    public JsonArray getArray(int index) {
        return this.contents.get(index).getAsArray();
    }

    public JsonObject getObject(int index) {
        return this.contents.get(index).getAsObject();
    }

    public JsonArray add(@Nullable String e) {
        if (e == null) {
            this.addNull();
        } else {
            this.contents.add(new JsonString(e));
        }

        return this;
    }

    public JsonArray add(@Nullable Number e) {
        if (e == null) {
            this.addNull();
        } else {
            this.contents.add(new JsonNumber(e));
        }

        return this;
    }

    public JsonArray addNull() {
        this.contents.add(JsonNull.INSTANCE);

        return this;
    }

    public JsonArray add(@Nullable JsonElement e) {
        if (e == null) {
            this.addNull();
        } else {
            this.contents.add(e);
        }

        return this;
    }

    /* Proxy */

    public int size() {
        return this.contents.size();
    }

    public boolean isEmpty() {
        return this.contents.isEmpty();
    }

    public boolean contains(@Nullable JsonElement e) {
        if (e == null) {
            e = JsonNull.INSTANCE;
        }

        return this.contents.contains(e);
    }

    @Override
    public Iterator<JsonElement> iterator() {
        return this.getList().iterator();
    }

    public boolean remove(@Nullable JsonElement e) {
        if (e == null) {
            e = JsonNull.INSTANCE;
        }

        return this.contents.remove(e);
    }

    public void clear() {
        this.contents.clear();
    }

    public JsonElement get(int index) {
        return this.contents.get(index);
    }

    public JsonElement set(int index, @Nullable JsonElement e) {
        if (e == null) {
            e = JsonNull.INSTANCE;
        }

        return this.contents.set(index, e);
    }

    public JsonElement remove(int index) {
        return this.contents.remove(index);
    }

    public int indexOf(@Nullable JsonElement e) {
        if (e == null) {
            e = JsonNull.INSTANCE;
        }

        return this.contents.indexOf(e);
    }

    public int lastIndexOf(@Nullable JsonElement e) {
        if (e == null) {
            e = JsonNull.INSTANCE;
        }

        return this.contents.lastIndexOf(e);
    }

    public JsonElement[] toArray() {
        return this.contents.toArray(new JsonElement[0]);
    }

    @Deprecated
    public List<JsonElement> getList() {
        return new ArrayList<>(this.contents);
    }

}
