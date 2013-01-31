/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.qp.operator;

import com.akiban.qp.row.PValuesRow;
import com.akiban.qp.row.Row;
import com.akiban.qp.row.ValuesRow;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.UserTableRowType;
import com.akiban.qp.rowtype.ValuesRowType;
import com.akiban.server.explain.CompoundExplainer;
import com.akiban.server.explain.ExplainContext;
import com.akiban.server.explain.std.CountOperatorExplainer;
import com.akiban.server.types.AkType;
import com.akiban.server.types3.mcompat.mtypes.MNumeric;
import com.akiban.server.types3.pvalue.PValue;
import com.akiban.util.ArgumentValidation;
import com.akiban.util.tap.InOutTap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**

 <h1>Overview</h1>

 Count_TableStatus returns the row count for a given RowType.

 <h1>Arguments</h1>

 <ul>

 <li><b>RowType tableType:</b> RowType of the table whose count is to be returned.

 </ul>


 <h1>Behavior</h1>

 The count of rows of the specified table is read out of the table's TableStatus.

 <h1>Output</h1>

 A single row containing the row count (type long).

 <h1>Assumptions</h1>

 None.

 <h1>Performance</h1>

 This operator does no IO.

 <h1>Memory Requirements</h1>

 This operator keeps no rows in memory.

 */

class Count_TableStatus extends Operator
{
    // Object interface

    @Override
    public String toString()
    {
        return String.format("%s(%s)", getClass().getSimpleName(), tableType);
    }

    // Operator interface

    @Override
    protected Cursor cursor(QueryContext context)
    {
        return new Execution(context);
    }

    @Override
    public RowType rowType()
    {
        return resultType;
    }

    @Override
    public void findDerivedTypes(Set<RowType> derivedTypes)
    {
        derivedTypes.add(resultType);
    }

    // Count_TableStatus interface

    public Count_TableStatus(RowType tableType, boolean usePValues)
    {
        ArgumentValidation.notNull("tableType", tableType);
        ArgumentValidation.isTrue("tableType instanceof UserTableRowType",
                                  tableType instanceof UserTableRowType);
        this.tableType = tableType;
        this.resultType = usePValues
                ? tableType.schema().newValuesType(MNumeric.BIGINT.instance(false))
                : tableType.schema().newValuesType(AkType.LONG);
        this.usePValues = usePValues;
    }

    // Class state
    
    private static final InOutTap TAP_OPEN = OPERATOR_TAP.createSubsidiaryTap("operator: Count_TableStatus open");
    private static final InOutTap TAP_NEXT = OPERATOR_TAP.createSubsidiaryTap("operator: Count_TableStatus next");
    private static final Logger LOG = LoggerFactory.getLogger(Count_TableStatus.class);

    // Object state

    private final RowType tableType;
    private final ValuesRowType resultType;
    private final boolean usePValues;

    @Override
    public CompoundExplainer getExplainer(ExplainContext context)
    {
        return new CountOperatorExplainer(getName(), tableType, resultType, null, context);
    }

    // Inner classes

    private class Execution extends OperatorExecutionBase implements Cursor
    {
        // Cursor interface

        @Override
        public void open()
        {
            TAP_OPEN.in();
            try {
                CursorLifecycle.checkIdle(this);
                pending = true;
            } finally {
                TAP_OPEN.out();
            }
        }

        @Override
        public Row next()
        {
            if (OPERATOR_NEXT_TAPS_ENABLED) {
                TAP_NEXT.in();
            }
            try {
                if (OPERATOR_CURSOR_LIFECYCLE_CHECKS_ENABLED) {
                    CursorLifecycle.checkIdleOrActive(this);
                }
                Row output;
                checkQueryCancelation();
                if (pending) {
                    long rowCount = adapter().rowCount(tableType);
                    close();
                    output = usePValues
                             ? new PValuesRow(resultType, new PValue(MNumeric.BIGINT.instance(false), rowCount))
                             : new ValuesRow(resultType, new Object[] { rowCount });
                }
                else {
                    output = null;
                }
                if (LOG_OPERATOR_EXECUTION && LOG.isDebugEnabled()) {
                    LOG.debug("Count_TableStatus: yield {}", output);
                }
                return output;
            } finally {
                if (OPERATOR_NEXT_TAPS_ENABLED) {
                    TAP_NEXT.out();
                }
            }
        }

        @Override
        public void close()
        {
            CursorLifecycle.checkIdleOrActive(this);
            pending = false;
        }

        @Override
        public void destroy()
        {
            destroyed = true;
        }

        @Override
        public boolean isIdle()
        {
            return !destroyed && !pending;
        }

        @Override
        public boolean isActive()
        {
            return !destroyed && pending;
        }

        @Override
        public boolean isDestroyed()
        {
            return destroyed;
        }

        // Execution interface

        Execution(QueryContext context)
        {
            super(context);
        }

        // Object state

        private boolean pending;
        private boolean destroyed = false;
    }
}
