package de.fhg.iais.roberta.syntax.actors.arduino;

import de.fhg.iais.roberta.syntax.action.Action;
import de.fhg.iais.roberta.syntax.lang.expr.Expr;
import de.fhg.iais.roberta.transformer.forClass.NepoPhrase;
import de.fhg.iais.roberta.transformer.forField.NepoField;
import de.fhg.iais.roberta.transformer.forField.NepoValue;
import de.fhg.iais.roberta.typecheck.BlocklyType;
import de.fhg.iais.roberta.util.ast.BlocklyProperties;
import de.fhg.iais.roberta.util.dbc.Assert;

@NepoPhrase(name = "L293D_MOTOR_RUN_ACTION", category = "ACTOR", blocklyNames = {"robActions_l293dmotor_run"})
public final class L293DMotorRunAction extends Action {
    @NepoField(name = "ACTORPORT")
    public final String port;

    @NepoValue(name = "POWER", type = BlocklyType.NUMBER_INT)
    public final Expr speed;

    public L293DMotorRunAction(BlocklyProperties properties, String port, Expr speed) {
        super(properties);
        Assert.notNull(port);
        Assert.notNull(speed);
        this.port = port;
        this.speed = speed;
        setReadOnly();
    }
}
