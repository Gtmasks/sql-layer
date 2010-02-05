package com.akiba.cserver.store;

import com.akiba.cserver.RowData;
import com.akiba.message.AkibaConnection;

/**
 * An abstraction for a layer that stores and retrieves data
 * 
 * @author peter
 * 
 */
public interface Store {

	public void startUp() throws Exception;

	public void shutDown() throws Exception;

	public RowCollector getCurrentRowCollector();


	
	public int writeRow(final RowData rowData) throws Exception;

	public long getAutoIncrementValue(final int rowDefId) throws Exception;

	public RowCollector newRowCollector(final int indexId, final RowData start,
			final RowData end, final byte[] columnBitMap) throws Exception;

	public long getRowCount(final boolean exact, final RowData start,
			final RowData end, final byte[] columnBitMap) throws Exception;
	
	public int dropTable(final int rowDefId) throws Exception;
}
