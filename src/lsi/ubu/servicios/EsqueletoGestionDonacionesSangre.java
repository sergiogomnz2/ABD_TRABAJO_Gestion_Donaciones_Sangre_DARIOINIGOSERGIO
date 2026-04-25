package lsi.ubu.enunciado;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
	
	//Transaccion 2: anular_traspaso
	public static void anular_traspaso(int m_ID_Tipo_Sangre, int m_ID_Hospital_Origen,int m_ID_Hospital_Destino,
			Date m_Fecha_Traspaso)
			throws SQLException {
		
		PoolDeConexiones pool = PoolDeConexiones.getInstance();
		Connection con=null;
				
		PreparedStatement psCheckTipoSangre  = null;
        PreparedStatement psCheckHospOrigen  = null;
        PreparedStatement psCheckHospDestino = null;
        PreparedStatement psSelectTraspasos  = null;
        PreparedStatement psRestaDestino     = null;
        PreparedStatement psSumaOrigen       = null;
        PreparedStatement psDelete           = null;
        ResultSet rs = null;
	
		try {
            con = pool.getConnection();

            //Verficamos que el tipo de sangre si que existe
            psCheckTipoSangre = con.prepareStatement(
                    "SELECT COUNT(*) FROM tipo_sangre WHERE id_tipo_sangre = ?");
            psCheckTipoSangre.setInt(1, m_ID_Tipo_Sangre);
            rs = psCheckTipoSangre.executeQuery();
            rs.next();
            if (rs.getInt(1) == 0) {
                throw new GestionDonacionesSangreException(
                        GestionDonacionesSangreException.TIPO_SANGRE_NO_EXISTE);
            }
            rs.close();  rs = null;
            psCheckTipoSangre.close(); psCheckTipoSangre = null;

            //Verifricamos que el hospital de origen si que existe
            psCheckHospOrigen = con.prepareStatement(
                    "SELECT COUNT(*) FROM hospital WHERE id_hospital = ?");
            psCheckHospOrigen.setInt(1, m_ID_Hospital_Origen);
            rs = psCheckHospOrigen.executeQuery();
            rs.next();
            if (rs.getInt(1) == 0) {
                throw new GestionDonacionesSangreException(
                        GestionDonacionesSangreException.HOSPITAL_NO_EXISTE);
            }
            rs.close();  rs = null;
            psCheckHospOrigen.close(); psCheckHospOrigen = null;

            //Verificamos que el hospital de destino si que existe
            psCheckHospDestino = con.prepareStatement(
                    "SELECT COUNT(*) FROM hospital WHERE id_hospital = ?");
            psCheckHospDestino.setInt(1, m_ID_Hospital_Destino);
            rs = psCheckHospDestino.executeQuery();
            rs.next();
            if (rs.getInt(1) == 0) {
                throw new GestionDonacionesSangreException(
                        GestionDonacionesSangreException.HOSPITAL_NO_EXISTE);
            }
            rs.close();  rs = null;
            psCheckHospDestino.close(); psCheckHospDestino = null;

            //Utilizando Sentencias Preparadas miramos que los traspasos coinciden con los parametros
            //Necesitamos las cantidades antes de borrar para poder revertir reservas
            psSelectTraspasos = con.prepareStatement(
                    "SELECT cantidad FROM traspaso "
                  + "WHERE id_tipo_sangre = ? "
                  + "  AND id_hospital_origen = ? "
                  + "  AND id_hospital_destino = ? "
                  + "  AND fecha_traspaso = ?");
            psSelectTraspasos.setInt(1, m_ID_Tipo_Sangre);
            psSelectTraspasos.setInt(2, m_ID_Hospital_Origen);
            psSelectTraspasos.setInt(3, m_ID_Hospital_Destino);
            psSelectTraspasos.setDate(4, new java.sql.Date(m_Fecha_Traspaso.getTime()));
            rs = psSelectTraspasos.executeQuery();

            //Declaramos Sentencias Preparadas para la actulizacion de las reservas del hospital Origen y Destino
            psRestaDestino = con.prepareStatement(
                    "UPDATE reserva_hospital "
                  + "SET cantidad = cantidad - ? "
                  + "WHERE id_tipo_sangre = ? AND id_hospital = ?");

            psSumaOrigen = con.prepareStatement(
                    "UPDATE reserva_hospital "
                  + "SET cantidad = cantidad + ? "
                  + "WHERE id_tipo_sangre = ? AND id_hospital = ?");

            boolean hayFilas = false;
            while (rs.next()) {
                hayFilas = true;
                float cantidad = rs.getFloat("cantidad");

                if (cantidad < 0) {
                    throw new GestionDonacionesSangreException(
                            GestionDonacionesSangreException.VALOR_CANTIDAD_TRASPASO_INCORRECTO);
                }

                //Restamos de Hospital Destino la cantidad y tipo de sangre usado
                psRestaDestino.setFloat(1, cantidad);
                psRestaDestino.setInt(2, m_ID_Tipo_Sangre);
                psRestaDestino.setInt(3, m_ID_Hospital_Destino);
                psRestaDestino.executeUpdate();

                //Sumamos al Hospital Origen la cantidad y tipo de sangre usado
                psSumaOrigen.setFloat(1, cantidad);
                psSumaOrigen.setInt(2, m_ID_Tipo_Sangre);
                psSumaOrigen.setInt(3, m_ID_Hospital_Origen);
                psSumaOrigen.executeUpdate();
            }
            rs.close();  rs = null;
            psSelectTraspasos.close(); psSelectTraspasos = null;

            //Excepcion en caso de que no haya ningun traspaso con esos criterios
            if (!hayFilas) {
                throw new GestionDonacionesSangreException(
                        GestionDonacionesSangreException.VALOR_CANTIDAD_TRASPASO_INCORRECTO);
            }

            //Eliminamos aquellos traspasos que ya hayan sido procesados
            psDelete = con.prepareStatement(
                    "DELETE FROM traspaso "
                  + "WHERE id_tipo_sangre = ? "
                  + "  AND id_hospital_origen = ? "
                  + "  AND id_hospital_destino = ? "
                  + "  AND fecha_traspaso = ?");
            psDelete.setInt(1, m_ID_Tipo_Sangre);
            psDelete.setInt(2, m_ID_Hospital_Origen);
            psDelete.setInt(3, m_ID_Hospital_Destino);
            psDelete.setDate(4, new java.sql.Date(m_Fecha_Traspaso.getTime()));
            psDelete.executeUpdate();
            psDelete.close(); psDelete = null;

            //commit
            con.commit();

        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ex) {
                    logger.error("Error en rollback: " + ex.getMessage());
                }
            }
            logger.error(e.getMessage());
            throw e;

        } finally {
            try { if (rs                != null) rs.close();                } catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (psCheckTipoSangre != null) psCheckTipoSangre.close(); } catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (psCheckHospOrigen != null) psCheckHospOrigen.close(); } catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (psCheckHospDestino!= null) psCheckHospDestino.close();} catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (psSelectTraspasos != null) psSelectTraspasos.close(); } catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (psRestaDestino    != null) psRestaDestino.close();    } catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (psSumaOrigen      != null) psSumaOrigen.close();      } catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (psDelete          != null) psDelete.close();          } catch (SQLException e) { logger.error(e.getMessage()); }
            try { if (con               != null) con.close();               } catch (SQLException e) { logger.error(e.getMessage()); }
        }
    }
	//Transacion 3: consuklta_traspasos
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

		//--------------------------------
        //tests de la primera transacción
        //--------------------------------
        System.out.println("\n Tests primera transacción (realizar donación)");

        //Test 1
        System.out.println("\nTest 1: Cantidad negativa -> excepcion codigo 5");
        try {
            realizar_donacion("12345678A", 1, -0.1f,
                    new java.util.Date());
            System.out.println("Error: no lanzó excepción");
        } catch (GestionDonacionesSangreException e) {
            System.out.println("Bien - codigo: " + e.getErrorCode() + " | " + e.getMessage());
        }

        //Test 2
        System.out.println("\nTest 2: Cantidad > 0.45 -> excepción código 5");
        try {
            realizar_donacion("12345678A", 1, 0.50f,
                    new java.util.Date());
            System.out.println("Error: no lanzó excepción");
        } catch (GestionDonacionesSangreException e) {
            System.out.println("Bien - código: " + e.getErrorCode() + " | " + e.getMessage());
        }

        //Test 3
        System.out.println("\nTest 3: NIF no existe -> excepcion codigo 1");
        try {
            realizar_donacion("00000000Z", 1, 0.25f,
                    new java.util.Date());
            System.out.println("Error: no lanzó excepción");
        } catch (GestionDonacionesSangreException e) {
            System.out.println("Bien - codigo: " + e.getErrorCode() + " | " + e.getMessage());
        }

        //Test 4
        System.out.println("\nTest 4: Hospital no existe -> excepción código 3");
        try {
            realizar_donacion("12345678A", 9999, 0.25f,
                    new java.util.Date());
            System.out.println("Error: no lanzó excepción");
        } catch (GestionDonacionesSangreException e) {
            System.out.println("Bien - codigo: " + e.getErrorCode() + " | " + e.getMessage());
        }

        //Test 5
        //El donante 12345678A tiene su última donación el 15/01/2025, intentamos donar el 20/01/2025 (5 dias, tendría que saltar la excepción)
        System.out.println("\nTest 5: Donacion en menos de 15 dias -> excepcion codigo 4");
        try {
            Date fechaNueva = Misc.addDays(
                    new java.sql.Date(new java.text.SimpleDateFormat("dd/MM/yyyy")
                            .parse("15/01/2025").getTime()), 5);
            realizar_donacion("12345678A", 1, 0.25f, fechaNueva);
            System.out.println("Error: no lanzó excepción");
        } catch (GestionDonacionesSangreException e) {
            System.out.println("Bien - codigo: " + e.getErrorCode() + " | " + e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        //Test 6
        //lo mismo de antes, pero aquí deberia funcionar
        System.out.println("\nTest 6: Donacion correcta -> sin excepcion");
        try {
            Date fechaValida = Misc.addDays(
                    new java.sql.Date(new java.text.SimpleDateFormat("dd/MM/yyyy")
                            .parse("25/01/2025").getTime()), 26);
            realizar_donacion("98989898C", 1, 0.30f, fechaValida);
            System.out.println("Bien - donacion realizada correctamente");
        } catch (Exception e) {
            System.out.println("Error inesperado: " + e.getMessage());
        }
        //Y esto cubre todos los casos de error

        //--------------------------------
        //Fin de tests de la primera transacción
        //--------------------------------
	}
}
