/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.qp.operator;

import com.akiban.ais.model.GroupTable;
import com.akiban.qp.row.HKey;
import com.akiban.qp.row.Row;
import com.akiban.util.ArgumentValidation;

class GroupScan_Default extends Operator
{
    // Object interface

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + '(' + cursorCreator + ' ' + limit + ')';
    }

    // Operator interface

    @Override
    protected Cursor cursor(StoreAdapter adapter)
    {
        return new Execution(adapter, cursorCreator, limit);
    }

    // GroupScan_Default interface

    public GroupScan_Default(GroupCursorCreator cursorCreator, Limit limit)
    {
        ArgumentValidation.notNull("groupTable", cursorCreator);
        this.limit = limit;
        this.cursorCreator = cursorCreator;
    }

    // Object state

    private final Limit limit;
    private final GroupCursorCreator cursorCreator;

    // Inner classes

    private static class Execution implements Cursor
    {

        // Cursor interface

        @Override
        public void open(Bindings bindings)
        {
            cursor.open(bindings);
        }

        @Override
        public Row next()
        {
            Row row;
            if ((row = cursor.next()) == null || limit.limitReached(row)) {
                close();
                row = null;
            }
            return row;
        }

        @Override
        public void close()
        {
            cursor.close();
        }

        // Execution interface

        Execution(StoreAdapter adapter, GroupCursorCreator cursorCreator, Limit limit)
        {
            this.adapter = adapter;
            this.cursor = cursorCreator.cursor(adapter);
            this.limit = limit;
        }

        // Object state

        private final StoreAdapter adapter;
        private final Cursor cursor;
        private final Limit limit;
    }

    static interface GroupCursorCreator
    {
        Cursor cursor(StoreAdapter adapter);

        GroupTable groupTable();
    }

    private static abstract class AbstractGroupCursorCreator implements GroupCursorCreator
    {

        // GroupCursorCreator interface

        @Override
        public final GroupTable groupTable()
        {
            return targetGroupTable;
        }


        // for use by subclasses

        protected AbstractGroupCursorCreator(GroupTable groupTable)
        {
            this.targetGroupTable = groupTable;
        }

        @Override
        public final String toString()
        {
            return describeRange() + " on " + targetGroupTable.getName().getTableName();
        }

        // for overriding in subclasses

        protected abstract String describeRange();

        private final GroupTable targetGroupTable;
    }

    static class FullGroupCursorCreator extends AbstractGroupCursorCreator
    {

        // GroupCursorCreator interface

        @Override
        public Cursor cursor(StoreAdapter adapter)
        {
            return adapter.newGroupCursor(groupTable());
        }

        // FullGroupCursorCreator interface

        public FullGroupCursorCreator(GroupTable groupTable)
        {
            super(groupTable);
        }

        // AbstractGroupCursorCreator interface

        @Override
        public String describeRange()
        {
            return "full scan";
        }
    }

    static class PositionalGroupCursorCreator extends AbstractGroupCursorCreator
    {

        // GroupCursorCreator interface

        @Override
        public Cursor cursor(StoreAdapter adapter)
        {
            return new HKeyBoundCursor(adapter.newGroupCursor(groupTable()), hKeyBindingPosition, deep);
        }

        // PositionalGroupCursorCreator interface

        PositionalGroupCursorCreator(GroupTable groupTable, int hKeyBindingPosition, boolean deep)
        {
            super(groupTable);
            this.hKeyBindingPosition = hKeyBindingPosition;
            this.deep = deep;
        }

        // AbstractGroupCursorCreator interface

        @Override
        public String describeRange()
        {
            return deep ? "deep hkey-bound scan" : "shallow hkey-bound scan";
        }

        // object state

        private final int hKeyBindingPosition;
        private final boolean deep;
    }

    private static class HKeyBoundCursor extends ChainedCursor
    {

        @Override
        public void open(Bindings bindings)
        {
            Object supposedHKey = bindings.get(hKeyBindingPosition);
            if (!(supposedHKey instanceof HKey)) {
                throw new RuntimeException(String.format("%s doesn't contain hkey at position %s",
                                                         bindings, hKeyBindingPosition));
            }
            HKey hKey = (HKey) supposedHKey;
            input.rebind(hKey, deep);
            input.open(bindings);
        }

        HKeyBoundCursor(GroupCursor input, int hKeyBindingPosition, boolean deep)
        {
            super(input);
            this.input = input;
            this.hKeyBindingPosition = hKeyBindingPosition;
            this.deep = deep;
        }

        private final GroupCursor input;
        private final int hKeyBindingPosition;
        private final boolean deep;
    }
}