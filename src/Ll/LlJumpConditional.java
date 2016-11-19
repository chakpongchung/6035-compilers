package Ll;

/**
 * Created by devinmorgan on 11/18/16.
 */
public class LlJumpConditional extends LlJump{
    private final LlComponent condition;

    public LlJumpConditional(String jumpToLabel, LlComponent condition) {
        super(jumpToLabel);
        this.condition = condition;
    }
}
