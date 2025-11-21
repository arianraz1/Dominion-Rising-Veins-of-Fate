package game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


GameState gs = new GameState()
@Test
void testBloodCannotBeNegative() {
    GameState.setBlood(-100);
    assertEquals(0, GameState.getBlood());
}