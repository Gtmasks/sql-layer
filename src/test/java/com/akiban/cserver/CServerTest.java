package com.akiban.cserver;

import java.io.File;

import junit.framework.TestCase;

import com.akiban.ais.ddl.DDLSource;
import com.akiban.ais.model.AkibaInformationSchema;
import com.akiban.ais.model.Type;
import com.akiban.cserver.message.GetAutoIncrementValueRequest;
import com.akiban.cserver.message.GetAutoIncrementValueResponse;
import com.akiban.cserver.message.WriteRowRequest;
import com.akiban.cserver.message.WriteRowResponse;
import com.akiban.cserver.store.PersistitStore;
import com.akiban.message.AkibaConnection;
import com.akiban.message.Message;
import com.akiban.message.MessageRegistryBase;
import com.akiban.network.AkibaNetworkHandler;
import com.akiban.network.NetworkHandlerFactory;

public class CServerTest extends TestCase implements CServerConstants {

	private final static File DATA_PATH = new File("/tmp/data");

	private final static String DDL_FILE_NAME = "src/test/resources/drupal_ddl_test.ddl";

	private final static RowDef ROW_DEF = RowDef.createRowDef(1234,
			new FieldDef[] { new FieldDef("a", FieldType.INT),
					new FieldDef("b", FieldType.INT),
					new FieldDef("c", FieldType.INT) }, "test",
			"group_table_test", new int[] { 0 });

	@Override
	public void setUp() throws Exception {
		CServerUtil.cleanUpDirectory(DATA_PATH);
		PersistitStore.setDataPath(DATA_PATH.getPath());
		MessageRegistryBase.reset();
	}

	// Currently it seems we can't run two tests in one class
	// with our NetworkHandler, so this one is removed.
	//
	public void dontTestWriteRowResponse() throws Exception {
		final CServer cserver = new CServer();
		cserver.getRowDefCache().putRowDef(ROW_DEF);
		cserver.start();
		try {
			final AkibaNetworkHandler networkHandler = NetworkHandlerFactory
					.getHandler("localhost", "8080", null);
			final AkibaConnection connection = AkibaConnection
					.createConnection(networkHandler);

			final WriteRowRequest request = createWriteRowRequest();

			final WriteRowResponse response = (WriteRowResponse) connection
					.sendAndReceive(request);
			assertEquals(1, response.getResultCode());

			networkHandler.disconnectWorker();
			NetworkHandlerFactory.closeNetwork();

		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			cserver.stop();
		}
	}

	public void testGetAutoIncrementRequestResponse() throws Exception {
		final CServer cserver = new CServer();
		cserver.getRowDefCache().putRowDef(ROW_DEF);
		ROW_DEF.setRowType(RowType.ROOT);
		ROW_DEF.setGroupRowDefId(ROW_DEF.getRowDefId());
		cserver.start();
		try {
			final AkibaNetworkHandler networkHandler = NetworkHandlerFactory
					.getHandler("localhost", "8080", null);
			final AkibaConnection connection = AkibaConnection
					.createConnection(networkHandler);
			Message request;
			Message response;

			request = new GetAutoIncrementValueRequest(ROW_DEF.getRowDefId());
			response = connection.sendAndReceive(request);
			assertEquals(-1, ((GetAutoIncrementValueResponse) response)
					.getValue());

			request = createWriteRowRequest();
			response = connection.sendAndReceive(request);
			assertEquals(OK, ((WriteRowResponse) response).getResultCode());

			request = new GetAutoIncrementValueRequest(ROW_DEF.getRowDefId());
			response = connection.sendAndReceive(request);
			assertEquals(1, ((GetAutoIncrementValueResponse) response)
					.getValue());

			networkHandler.disconnectWorker();
			NetworkHandlerFactory.closeNetwork();

		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			cserver.stop();
		}
	}

	/**
	 * Make sure we can load the Drupal schema via DDLSource.  Padraig encountered
	 * a problem with an incorrectly written schema file.  Leaving this test here
	 * for now in case we need to test newer versions.
	 */
	public void testDrupalSchema() throws Exception {
		final AkibaInformationSchema ais = new DDLSource()
				.buildAIS(DDL_FILE_NAME);
		final CServer cserver = new CServer();
		cserver.getRowDefCache().setAIS(ais);
	}
	
	public void testEmitTypes() throws Exception {
		final AkibaInformationSchema ais = new DDLSource()
			.buildAIS(DDL_FILE_NAME);
		for (final Type t : ais.getTypes()) {
			System.out.println(String.format("<type name=\"%s\" nparams=\"%s\" fixed=\"%s\" maxsize=\"%s\" />",
					t.name(), t.nTypeParameters(), t.fixedSize(), t.maxSizeBytes()));
		}
	}

	private WriteRowRequest createWriteRowRequest() {
		final RowData rowData = new RowData(new byte[64]);
		rowData.createRow(ROW_DEF, new Object[] { 1, 2, 3 });
		final WriteRowRequest request = new WriteRowRequest();
		request.setRowData(rowData);
		return request;
	}

}
