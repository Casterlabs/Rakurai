# Json 
Our very own Json library, Rson.

## Adding to your project

### Maven
```xml
    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>com.github.casterlabs.rakurai</groupId>
            <artifactId>Json</artifactId>
            <version>VERSION</version>
            <scope>compile</scope>
        </dependency>
    </dependencies>
```

### Gradle
```gradle
    allprojects {
        repositories {
            maven { url 'https://jitpack.io' }
        }
	}

    dependencies {
        implementation 'com.github.casterlabs.rakurai:Json:VERSION'
    }
```

## Example Code

### Creating Json directly.
```java
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

System.out.println(
    new Rson.Builder()
        .setJson5FeaturesEnabled(true)
        .setPrettyPrintingEnabled(true)
        .build()
        .toJsonString(obj)
);

// Output:
/*
{
    test_num: 1234.5678,
    test_str: "The quick brown fox jumped over the lazy dog.",
    test_arr: [
        "Lorem",
        "ipsum",
        "dolor",
        "sit",
        "amet"
    ],
    test_obj: {
        java: "rocks!"
    },
    test_null: null,
    "0__non_json5_friendly_field_name": null
}
*/

// System.out.println(obj); Also works.

```

### Full Demo
[Demo File](https://github.com/Casterlabs/Rakurai/blob/main/Json/JsonDemo.java)  
[Validation Demo File](https://github.com/Casterlabs/Rakurai/blob/main/Json/JsonValidationDemo.java)  
  
That demo outputs this:  
```
Objects are easily serialized.
{
    "number": 1234,
    "my_string": "I love Rakurai <3",
    "my_string_two": "I also love Rakurai <3"
}

It even works for arrays and collections...
[
    {
        "number": 1234,
        "my_string": "I love Rakurai <3",
        "my_string_two": "I also love Rakurai <3"
    }
]

Maps work as well.
{
    "test3": {
        "number": 1234,
        "my_string": "I love Rakurai <3",
        "my_string_two": "I also love Rakurai <3"
    }
}

We can also deserialize back into a TestObject.
{"my_string": "My String","my_string_two": "My String Two"}
Test.TestObject(number=1234, myString=My String, deux=Test.TestObjectDeux(str=My String Two))

Deserializing into arrays also works.
[{"my_string": "My String","my_string_two": "My String Two"}]
[Test.TestObject(number=1234, myString=My String, deux=Test.TestObjectDeux(str=My String Two))]


 Serialization demo took 3 ms, Deserialization demo took 2 ms.
```