package lsi.ubu.enunciado;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lsi.ubu.util.ExecuteScript;
import lsi.ubu.util.PoolDeConexiones;


/**
 * GestionDonacionesSangre:
 * Implementa la gestion de donaciones de sangre según el enunciado del ejercicio
 * 
 * @author <a href="mailto:jmaudes@ubu.es">Jesus Maudes</a>
 * @author <a href="mailto:rmartico@ubu.es">Raul Marticorena</a>
 * @author <a href="mailto:pgdiaz@ubu.es">Pablo Garcia</a>
 * @author <a href="mailto:srarribas@ubu.es">Sandra Rodriguez</a>
 * @version 1.5
 * @since 1.0 
 */
public class EsqueletoGestionDonacionesSangre {
	
	private static Logger logger = LoggerFactory.getLogger(EsqueletoGestionDonacionesSangre.class);

	private static final String script_path = "sql/";

	public static void main(String[] args) throws SQLException{		
		tests();

		System.out.println("FIN.............");
	}

	//Transaccion 1: realizar donación
	public static void realizar_donacion(String m_NIF, int m_ID_Hospital,
			float m_Cantidad,  Date m_Fecha_Donacion) throws SQLException {
		
		PoolDeConexiones pool = PoolDeConexiones.getInstance();
		Connection con=null;

	
		try{
			con = pool.getConnection();
			
			PreparedStatement psCheckDonante = null;
			PreparedStatement psCheckHospital = null;
			PreparedStatement psUltimaDonacion = null;
			PreparedStatement psInsert = null;
			PreparedStatement psUpdateReserva = null;
			
			ResultSet rs = null;
			

			//Comprobamos que la cantidad esté entre 0 y 0.45
			if (m_Cantidad <= 0 || m_Cantidad > 0.45f) {
                throw new GestionDonacionesSangreException(
                        GestionDonacionesSangreException.VALOR_CANTIDAD_DONACION_INCORRECTO);
            }

			//Comprobamos que el donante existe
			psCheckDonante = con.prepareStatement(
                    "SELECT COUNT(*) FROM donante WHERE NIF = ?");
            psCheckDonante.setString(1, m_NIF);
            rs = psCheckDonante.executeQuery();
            rs.next();
            if (rs.getInt(1) == 0) {
				//Si el DNI no aparece es que no existe y salta la excepcion
                throw new GestionDonacionesSangreException(
                        GestionDonacionesSangreException.DONANTE_NO_EXISTE);
            }
            rs.close();   rs = null;
            psCheckDonante.close(); psCheckDonante = null;

			//Lo mismo para hospital
			psCheckHospital = con.prepareStatement(
                    "SELECT COUNT(*) FROM hospital WHERE id_hospital = ?");
            psCheckHospital.setInt(1, m_ID_Hospital);
            rs = psCheckHospital.executeQuery();
            rs.next();
            if (rs.getInt(1) == 0) {
                throw new GestionDonacionesSangreException(
                        GestionDonacionesSangreException.HOSPITAL_NO_EXISTE);
            }
            rs.close();		rs = null;
            psCheckHospital.close(); psCheckHospital = null;


			//Aqui comprobamos la regla de los 15 dias 
			//Buscamos la donacion mas reciente
			psUltimaDonacion = con.prepareStatement(
                    "SELECT MAX(fecha_donacion) FROM donacion WHERE nif_donante = ?");
            psUltimaDonacion.setString(1, m_NIF);
			//Como ya hemos comprobado el DNI esto pasa sin problemas
            rs = psUltimaDonacion.executeQuery();
            if (rs.next()) {
                java.sql.Date ultimaSQL = rs.getDate(1);
                if (ultimaSQL != null) { //Hay que mirar que no sea null porque MAX siepre devuelve algo (incluido null)
                    Date ultima = new Date(ultimaSQL.getTime());
                    int dias = Misc.howManyDaysBetween(
                            Misc.truncDate(m_Fecha_Donacion),
                            Misc.truncDate(ultima));
                    if (dias < 15) {
						//Si no se cumple salta la excepcion
                        throw new GestionDonacionesSangreException(
                                GestionDonacionesSangreException.DONANTE_EXCEDE);
                    }
                }
            }
            rs.close();         rs = null;
            psUltimaDonacion.close(); psUltimaDonacion = null;


			//Insertamos la donacion
			//seq.donacion.nextval general la clave primaria
			psInsert = con.prepareStatement(
                    "INSERT INTO donacion (id_donacion, nif_donante, cantidad, fecha_donacion) "
                  + "VALUES (seq_donacion.nextval, ?, ?, ?)");
            psInsert.setString(1, m_NIF);
            psInsert.setFloat(2, m_Cantidad);
            psInsert.setDate(3, new java.sql.Date(m_Fecha_Donacion.getTime()));
            psInsert.executeUpdate();
            psInsert.close(); psInsert = null;


			//Actualizamos la reserva
			//El tipo de sangre se saca de la tabla donante con la subconsulta
			psUpdateReserva = con.prepareStatement(
                    "UPDATE reserva_hospital "
                  + "SET cantidad = cantidad + ? "
                  + "WHERE id_hospital = ? "
                  + "  AND id_tipo_sangre = (SELECT id_tipo_sangre FROM donante WHERE NIF = ?)");
            psUpdateReserva.setFloat(1, m_Cantidad);
            psUpdateReserva.setInt(2, m_ID_Hospital);
            psUpdateReserva.setString(3, m_NIF);
            psUpdateReserva.executeUpdate();
            psUpdateReserva.close(); psUpdateReserva = null;


			//Si llega hasta aqui hace el commit
			con.commit()
			
		} catch (SQLException e) {
			if (con != null) {
                try { con.rollback(); } catch (SQLException ex) {
                    logger.error("Error en rollback: " + ex.getMessage());
                }
            }
			logger.error(e.getMessage());
			throw e;		

		} finally {
			//Y ya para acabar libera recursos pase lo que pase
			try { if (rs               != null) rs.close();               } catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (psCheckDonante   != null) psCheckDonante.close();   } catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (psCheckHospital  != null) psCheckHospital.close();  } catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (psUltimaDonacion != null) psUltimaDonacion.close(); } catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (psInsert         != null) psInsert.close();         } catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (psUpdateReserva  != null) psUpdateReserva.close();  } catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (con              != null) con.close();              } catch (SQLException e) { logger.error(e.getMessage()); }
		}
		
		
	}
	
	public static void anular_traspaso(int m_ID_Tipo_Sangre, int m_ID_Hospital_Origen,int m_ID_Hospital_Destino,
			Date m_Fecha_Traspaso)
			throws SQLException {
		
		PoolDeConexiones pool = PoolDeConexiones.getInstance();
		Connection con=null;

	
		try{
			con = pool.getConnection();
			//Completar por el alumno
			
		} catch (SQLException e) {
			//Completar por el alumno			
			
			logger.error(e.getMessage());
			throw e;		

		} finally {
			/*A rellenar por el alumno*/
		}		
	}
	
	public static void consulta_traspasos(String m_Tipo_Sangre)
			throws SQLException {

				
		PoolDeConexiones pool = PoolDeConexiones.getInstance();
		Connection con=null;

	
		try{
			con = pool.getConnection();
			//Completar por el alumno
			
		} catch (SQLException e) {
			//Completar por el alumno			
			
			logger.error(e.getMessage());
			throw e;		

		} finally {
			/*A rellenar por el alumno*/
		}		
	}
	
	static public void creaTablas() {
		ExecuteScript.run(script_path + "gestion_donaciones_sangre.sql");
	}

	static void tests() throws SQLException{
		creaTablas();
		
		PoolDeConexiones pool = PoolDeConexiones.getInstance();		
		
		//Relatar caso por caso utilizando el siguiente procedure para inicializar los datos
		
		CallableStatement cll_reinicia=null;
		Connection conn = null;
		
		try {
			//Reinicio filas
			conn = pool.getConnection();
			cll_reinicia = conn.prepareCall("{call inicializa_test}");
			cll_reinicia.execute();
		} catch (SQLException e) {				
			logger.error(e.getMessage());			
		} finally {
			if (cll_reinicia!=null) cll_reinicia.close();
			if (conn!=null) conn.close();
		
		}			
		
	}
}
