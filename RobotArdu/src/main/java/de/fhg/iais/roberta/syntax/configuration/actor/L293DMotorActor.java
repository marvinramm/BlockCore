package de.fhg.iais.roberta.syntax.configuration.actor;

import de.fhg.iais.roberta.syntax.configuration.ConfigurationComponent;
import de.fhg.iais.roberta.transformer.forClass.NepoConfiguration;
import de.fhg.iais.roberta.util.dbc.DbcException;

@NepoConfiguration(name = "L293D_MOTOR", category = "CONFIGURATION_ACTOR", blocklyNames = {"robConf_l293dmotor"})
public final class L293DMotorActor extends ConfigurationComponent {
    private L293DMotorActor() {
        super(null, null, null, null, null);
        throw new DbcException("should NEVER be called");
    }
}
