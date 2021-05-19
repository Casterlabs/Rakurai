package co.casterlabs.rakurai;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.rakurai.json.JsonClass;
import co.casterlabs.rakurai.json.JsonField;
import co.casterlabs.rakurai.json.JsonSerializer;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rakurai.json.element.JsonString;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import lombok.NonNull;
import lombok.ToString;

public class Test {

    public static void main(String[] args) throws Exception {
        long start1 = System.currentTimeMillis();
        demoCreation();
        long end1 = System.currentTimeMillis();

        System.gc();

        long start2 = System.currentTimeMillis();
        demoSerialization();
        long end2 = System.currentTimeMillis();

        System.gc();

        long start3 = System.currentTimeMillis();
        demoDeserialization();
        long end3 = System.currentTimeMillis();

        System.out.printf(
            "\n\nBenchmarks: %dms for the creation demo, %dms for the serialization demo, and %dms for the deserialization demo\n",
            end1 - start1,
            end2 - start2,
            end3 - start3
        );
    }

    public static void demoCreation() {
        // Has convenience constructors
        JsonArray array = new JsonArray("Lorem", "ipsum", "dolor", "sit", "amet");

        // Supports a builder-like syntax
        JsonObject obj = new JsonObject()
            .put("test_num", 1234.5678)
            .put("test_str", "The quick brown fox jumped over the lazy dog.")
            .put("test_arr", array)
            .put("test_obj", JsonObject.singleton("java", "rocks!"))
            .putNull("test_null")
            .putNull("0__non_json5_friendly_field_name");

        System.out.println("It is easy to create json without the need of POJOs: (JSON5 & pretty printing are enabled for this example)");
        System.out.println(
            new Rson.Builder()
                .setJson5FeaturesEnabled(true)
                .setPrettyPrintingEnabled(true)
                .build()
                .toJsonString(obj)
        );
    }

    public static void demoSerialization() throws Exception {
        System.out.println("\nObjects are easily serialized.");

        TestObject test = new TestObject();

        System.out.println(Rson.DEFAULT.toJson(test));

        //

        System.out.println("\nIt even works for arrays and collections...");

        System.out.println(Rson.DEFAULT.toJson(Arrays.asList(test)));

        //

        System.out.println("\nMaps work as well.");

        Map<String, TestObject> test3 = Collections.singletonMap("test3", test);

        System.out.println(Rson.DEFAULT.toJson(test3));
    }

    public static void demoDeserialization() throws Exception {
        System.out.println("\nWe can also deserialize back into a TestObject.");

        // It can also handle missing values, a non present value won't do anything.
        // A null value will set the field to null (or 0 or false, for primitives)
        String json = "{\"my_string\": \"My String\",\"my_string_two\": \"My String Two\"}";

        System.out.println(json);

        TestObject deser_test = Rson.DEFAULT.fromJson(json, TestObject.class);

        System.out.println(deser_test);

        //

        System.out.println("\nDeserializing into arrays also works.");

        String json2 = "[{\"my_string\": \"My String\",\"my_string_two\": \"My String Two\"}]";

        System.out.println(json2);

        TestObject[] deser_test2 = Rson.DEFAULT.fromJson(json2, TestObject[].class);

        System.out.println(Arrays.toString(deser_test2));
    }

    @ToString
    public static class TestObject {
        // Fields must be exposed via @JsonField, or they will be left out entirely.
        @JsonField
        private int number = 1234;

        // You can construct the annotation with a preferred name,
        // it'll be respected during serialization and deserialization.
        @JsonField("my_string")
        private String myString = "I love Rakurai <3";

        @JsonField("my_string_two")
        private TestObjectDeux deux = new TestObjectDeux();

    }

    // Using lombok's ToString annotation for convenience.
    @ToString
    @JsonClass(serializer = TestObjectDeuxSerializer.class)
    public static class TestObjectDeux {
        private String str = "I also love Rakurai <3";

    }

    public static class TestObjectDeuxSerializer implements JsonSerializer<TestObjectDeux> {

        @Override
        public JsonElement serialize(@NonNull Object value, @NonNull Rson rson) {
            // You can override the serialization and deserialization
            // with your own custom implementation.
            return new JsonString(((TestObjectDeux) value).str);
        }

        @Override
        public @Nullable TestObjectDeux deserialize(@NonNull JsonElement value, @NonNull Class<?> type, @NonNull Rson rson) throws JsonParseException {
            // Here we reconstruct it for the deserialization example.
            TestObjectDeux t = new TestObjectDeux();

            t.str = value.getAsString();

            return t;
        }

    }

}
