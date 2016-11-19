package Ll;

/**
 * Created by devinmorgan on 11/18/16.
 */
public abstract class LlJump extends LlStatement {
    private final String jumpToLabel;

    public LlJump(String jumpToLabel) {
        this.jumpToLabel = jumpToLabel;
    }
}
