package lsi.ubu.servicios;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lsi.ubu.excepciones.AlquilerCochesException;
import lsi.ubu.util.PoolDeConexiones;

public class ServicioImpl implements Servicio {
	private static final Logger LOGGER = LoggerFactory.getLogger(ServicioImpl.class);

	private static final int DIAS_DE_ALQUILER = 4;

	public void alquilar(String nifCliente, String matricula, Date fechaIni, Date fechaFin) throws SQLException {
		 PoolDeConexiones pool = PoolDeConexiones.getInstance();
	        Connection con = null;
	        PreparedStatement st = null;
	        ResultSet rs = null;

	        long diasDiff = DIAS_DE_ALQUILER;
	        if (fechaFin != null) {
	            diasDiff = TimeUnit.MILLISECONDS.toDays(fechaFin.getTime() - fechaIni.getTime());

	            if (diasDiff < 1) {
	                throw new AlquilerCochesException(AlquilerCochesException.SIN_DIAS);
	            }
	        } else {
	            fechaFin = new Date(fechaIni.getTime() + TimeUnit.DAYS.toMillis(DIAS_DE_ALQUILER));
	        }

	        try {
	            con = pool.getConnection();
	            con.setAutoCommit(false);

	            // 1. Verificar que el cliente existe
	            st = con.prepareStatement("SELECT 1 FROM clientes WHERE nif = ?");
	            st.setString(1, nifCliente);
	            rs = st.executeQuery();
	            if (!rs.next()) {
	            	throw new AlquilerCochesException(AlquilerCochesException.CLIENTE_NO_EXIST);
	            }
	            rs.close();
	            st.close();

	            // 2. Verificar que el vehículo existe
	            st = con.prepareStatement("SELECT 1 FROM vehiculos WHERE matricula = ?");
	            st.setString(1, matricula);
	            rs = st.executeQuery();
	            if (!rs.next()) {
	            	throw new AlquilerCochesException(AlquilerCochesException.VEHICULO_NO_EXIST);
	            }
	            rs.close();
	            st.close();

	            // 3. Verificar que no hay solapamiento con reservas existentes
	            String sqlSolape = """
	                SELECT 1 FROM reservas
	                WHERE matricula = ?
	                  AND NOT (fecha_fin < ? OR fecha_ini > ?)
	            """;
	            st = con.prepareStatement(sqlSolape);
	            st.setString(1, matricula);
	            st.setDate(2, new java.sql.Date(fechaIni.getTime()));
	            st.setDate(3, new java.sql.Date(fechaFin.getTime()));
	            rs = st.executeQuery();
	            if (rs.next()) {
	                throw new AlquilerCochesException(AlquilerCochesException.VEHICULO_OCUPADO);
	            }
	            rs.close();
	            st.close();

	            // 4. Insertar reserva
	            String sqlInsert = """
	                INSERT INTO reservas (idReserva, cliente, matricula, fecha_ini, fecha_fin)
	                VALUES (seq_reservas.nextval, ?, ?, ?, ?)
	            """;
	            st = con.prepareStatement(sqlInsert);
	            st.setString(1, nifCliente);
	            st.setString(2, matricula);
	            st.setDate(3, new java.sql.Date(fechaIni.getTime()));
	            st.setDate(4, new java.sql.Date(fechaFin.getTime()));
	            st.executeUpdate();
	            st.close();

	            con.commit();

	        } catch (SQLException e) {
	            if (con != null) {
	                try {
	                    con.rollback();
	                    LOGGER.error("Transacción deshecha debido a un error", e);
	                } catch (SQLException ex) {
	                    LOGGER.error("Error al hacer rollback", ex);
	                }
	            }

	            // Logear cualquier otra excepción no relacionada con AlquilerCochesException
	            if (!(e instanceof AlquilerCochesException)) {
	                LOGGER.error("Error inesperado", e);
	            }
	            throw e;

	        } finally {
	            try {
	                if (rs != null) rs.close();
	            } catch (SQLException ignored) {}

	            try {
	                if (st != null) st.close();
	            } catch (SQLException ignored) {}

	            try {
	                if (con != null) {
	                    con.setAutoCommit(true);
	                    con.close();
	                }
	            } catch (SQLException ignored) {}
	        }
	    }
}
