package de.fhg.iais.roberta.visitor.codegen;

import java.util.List;

import com.google.common.collect.ClassToInstanceMap;

import de.fhg.iais.roberta.bean.IProjectBean;
import de.fhg.iais.roberta.components.ConfigurationAst;
import de.fhg.iais.roberta.syntax.Phrase;
import de.fhg.iais.roberta.util.dbc.DbcException;
import de.fhg.iais.roberta.visitor.IVisitor;

/**
 * This class is implementing {@link IVisitor}. All methods are implemented and they append a human-readable C representation of a phrase to a StringBuilder.
 * <b>This representation is correct C code for Arduino.</b> <br>
 */
public class ArduinoCppVisitor extends CommonArduinoCppVisitor {

    /**
     * Initialize the C++ code generator visitor.
     *
     * @param phrases to generate the code from
     */
    public ArduinoCppVisitor(List<List<Phrase>> phrases, ConfigurationAst brickConfiguration, ClassToInstanceMap<IProjectBean> beans) {
        super(phrases, brickConfiguration, beans);
    }

}
