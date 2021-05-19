package co.casterlabs.rakurai.json.serialization;

import co.casterlabs.rakurai.json.JsonUtil;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class JsonSerializationContext {
    private StringBuilder sb = new StringBuilder();

    private int indentLevel = 0;
    private boolean needsComma = false;
    private boolean addedContent = false;
    private boolean indentIfNotDangle = false;

    private @Setter @Getter boolean prettyPrinting = false;
    private @Setter @Getter boolean json5Enabled = false;
    private @Setter @Getter String tabCharacter = "    ";

    /* Object */

    public JsonSerializationContext startObject() {
        this.addedContent = false;
        this.insertCommaAndIndent();

        this.sb.append('{');

        this.indentLevel++;

        this.needsComma = false;
        this.indentIfNotDangle = true;
        return this;
    }

    public JsonSerializationContext startObjectEntry(@NonNull String key) {
        this.insertCommaAndIndent();

        this.indentLevel++;

        this.sb
            .append(this.getFieldName(key))
            .append(':')
            .append(this.getOptionalSpace())
            .append('{');

        this.needsComma = false;
        this.addedContent = false;
        this.indentIfNotDangle = true;
        return this;
    }

    public JsonSerializationContext insertObjectString(@NonNull String key, @NonNull String value) {
        this.addedContent = true;
        this.insertCommaAndIndent();

        this.sb
            .append(this.getFieldName(key))
            .append(':')
            .append(this.getOptionalSpace())
            .append('"')
            .append(JsonUtil.jsonEscape(value)).append('"');

        this.needsComma = true;
        return this;
    }

    public JsonSerializationContext insertObjectNumber(@NonNull String key, @NonNull Number value) {
        this.addedContent = true;
        this.insertCommaAndIndent();

        this.sb
            .append(this.getFieldName(key))
            .append(':')
            .append(this.getOptionalSpace())
            .append(JsonUtil.jsonStringNumber(value));

        this.needsComma = true;
        return this;
    }

    public JsonSerializationContext insertObjectBoolean(@NonNull String key, boolean value) {
        this.addedContent = true;
        this.insertCommaAndIndent();

        this.sb
            .append(this.getFieldName(key))
            .append(':')
            .append(this.getOptionalSpace())
            .append(value);

        this.needsComma = true;
        return this;
    }

    public JsonSerializationContext insertObjectNull(@NonNull String key) {
        this.addedContent = true;
        this.insertCommaAndIndent();

        this.sb
            .append(this.getFieldName(key))
            .append(':')
            .append(this.getOptionalSpace())
            .append("null");

        this.needsComma = true;
        return this;
    }

    public JsonSerializationContext endObject() {
        this.indentLevel--;

        this.indent(true);

        this.sb.append('}');
        this.needsComma = true;
        this.addedContent = true;
        return this;
    }

    /* Array */

    public JsonSerializationContext startArray() {
        this.insertCommaAndIndent();

        this.indentLevel++;

        this.sb.append('[');

        this.needsComma = false;
        this.addedContent = false;
        this.indentIfNotDangle = true;
        return this;
    }

    public JsonSerializationContext startArrayEntry(@NonNull String key) {
        this.insertCommaAndIndent();

        this.indentLevel++;

        this.sb
            .append(this.getFieldName(key))
            .append(':')
            .append(this.getOptionalSpace())
            .append('[');

        this.needsComma = false;
        this.addedContent = false;
        this.indentIfNotDangle = true;
        return this;
    }

    public JsonSerializationContext insertArrayString(@NonNull String value) {
        this.addedContent = true;
        this.insertCommaAndIndent();

        this.sb
            .append('"')
            .append(JsonUtil.jsonEscape(value))
            .append('"');

        this.needsComma = true;
        return this;
    }

    public JsonSerializationContext insertArrayNumber(@NonNull Number value) {
        this.addedContent = true;
        this.insertCommaAndIndent();

        this.sb.append(JsonUtil.jsonStringNumber(value));

        this.needsComma = true;
        return this;
    }

    public JsonSerializationContext insertArrayBoolean(boolean value) {
        this.addedContent = true;
        this.insertCommaAndIndent();

        this.sb.append(value);

        this.needsComma = true;
        return this;
    }

    public JsonSerializationContext insertArrayNull() {
        this.addedContent = true;
        this.insertCommaAndIndent();

        this.sb.append("null");

        this.needsComma = true;
        return this;
    }

    public JsonSerializationContext endArray() {
        this.indentLevel--;

        this.indent(true);

        this.sb.append(']');
        this.needsComma = true;
        this.addedContent = true;
        return this;
    }

    /* Misc */

    public JsonSerializationContext insertRaw(@NonNull String contents) {
        this.sb.append(contents);
        return this;
    }

    private String getFieldName(String field) {
        if (this.json5Enabled && JsonUtil.isValidJson5FieldName(field)) {
            return field;
        } else {
            return String.format("\"%s\"", JsonUtil.jsonEscape(field));
        }
    }

    public void insertCommaAndIndent() {
        if (this.needsComma) {
            this.sb.append(',');
        }

        this.indent(false);
    }

    public void indent(boolean dangling) {
        if (this.prettyPrinting) {
            if (this.needsComma || this.addedContent || (this.indentIfNotDangle && !dangling)) {
                this.sb.append('\n');

                for (int i = 0; i < this.indentLevel; i++) {
                    this.sb.append(this.tabCharacter);
                }
            }
        }

        this.indentIfNotDangle = false;
    }

    private String getOptionalSpace() {
        return this.prettyPrinting ? " " : "";
    }

    @Override
    public String toString() {
        return this.sb.toString();
    }

}
