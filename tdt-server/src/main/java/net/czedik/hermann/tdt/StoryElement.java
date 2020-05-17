package net.czedik.hermann.tdt;

public class StoryElement {
    public String type;
    public String content;

    private StoryElement(String type, String content) {
        this.type = type;
        this.content = content;
    }

    public static StoryElement createImageElement(String filename) {
        return new StoryElement("image", filename);
    }

    public static StoryElement createTextElement(String text) {
        return new StoryElement("text", text);
    }
}
