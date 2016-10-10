package edu.mit.compilers.ir;

import edu.mit.compilers.ir.Ir;
import edu.mit.compilers.ir.IrIdent;

/**
 * Created by devinmorgan on 10/5/16.
 */
public abstract class IrLocation extends IrExpr {
    private final IrIdent varName;

    public IrLocation(IrIdent varName, int lineNumber, int colNumber) {
        super(lineNumber, colNumber);
        this.varName = varName;
    }
}