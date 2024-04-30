package lsi.ubu.servicios;

import java.sql.SQLException;
import java.sql.Time;

public interface Servicio {

	public void anularBillete(Time p_hora, java.util.Date p_fecha, String p_origen, String p_destino, int p_nroPlazas,
			int p_ticket) throws SQLException;

	public void comprarBillete(Time p_hora, java.util.Date p_fecha, String p_origen, String p_destino, int p_nroPlazas)
			throws SQLException;
}
