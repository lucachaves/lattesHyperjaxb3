

import generated.*;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.dom.DOMResult;

import org.apache.commons.lang3.StringUtils;

import junit.framework.TestCase;

public class InsertCurriculum {

	private ObjectFactory objectFactory;
	private EntityManagerFactory entityManagerFactory;
	private JAXBContext context;
	private Connection connectionLattesXML = null;
	private Connection connectionLattesRDB = null;
	private EntityManager loadManager;
	private EntityManager saveManager;
	
	public InsertCurriculum() {
	}

	private void setUp() throws Exception {
		objectFactory = new ObjectFactory();
		final Properties persistenceProperties = new Properties();
		InputStream is = null;
		try {
			is = getClass().getClassLoader().getResourceAsStream(
					"persistence.properties");
			persistenceProperties.load(is);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ignored) {
				}
			}
		}

		entityManagerFactory = Persistence.createEntityManagerFactory(
				"generated", persistenceProperties);
		context = JAXBContext.newInstance("generated");
		saveManager = entityManagerFactory
				.createEntityManager();
		loadManager = entityManagerFactory
				.createEntityManager();
		Class.forName("org.postgresql.Driver");
		connectionLattesXML = DriverManager
				.getConnection("jdbc:postgresql://10.0.1.7:5432/lattesdata",
						"postgres", "postgres");
		connectionLattesRDB = DriverManager
				.getConnection("jdbc:postgresql://10.0.1.7:5432/curriculum",
						"postgres", "postgres");
	}
	
	private void tearDown() throws SQLException {
		connectionLattesXML.close();
		connectionLattesRDB.close();
		loadManager.close();
		saveManager.close();
	}

	private HashMap<Integer, String> getXmlIds() throws SQLException{
		HashMap<Integer, String> xmls = new HashMap<Integer, String>();
		
		Statement stmt = connectionLattesRDB.createStatement();
		ResultSet rs = stmt.executeQuery("");
		List<String> excludeIds = new ArrayList<String>();
		while(rs.next()){
			excludeIds.add(rs.getString("numeroidentificador"));
		}
		String sql = "SELECT curriculums.id, curriculums.id16 FROM public.curriculums WHERE curriculums.degree ILIKE '%doutorado%' AND curriculums.id16 NOT IN ('"+StringUtils.join(excludeIds, "', '")+"') ORDER BY curriculums.id DESC;";
		stmt = connectionLattesXML.createStatement();
		rs = stmt.executeQuery(sql);
		while ( rs.next() ) {
            Integer id = rs.getInt("id");
            String  id16 = rs.getString("id16");
            xmls.put(id, id16);
//            System.out.print( " # " );
         }
         rs.close();
         stmt.close();
        
		return xmls;
	}
	
	private String getXml(Integer id) throws SQLException{
		String xml = null;
		
		Statement stmt = connectionLattesXML.createStatement();
        ResultSet rs = stmt.executeQuery( "SELECT curriculums.xml FROM public.curriculums WHERE id = "+id+";" );
        while ( rs.next() ) {
            xml = rs.getString("xml");
         }
         rs.close();
         stmt.close();
		
		return xml;
	}
		
	private void removeCurriculum(Integer id) throws SQLException {
		saveManager.getTransaction().begin();
		Statement stmt = connectionLattesXML.createStatement();
		CurriculoVitaeType c = saveManager.find(CurriculoVitaeType.class, id);
        saveManager.remove(c);
		saveManager.getTransaction().commit();
	}

	
	private Boolean hasCurriculum(String id16) throws SQLException{
		String id = "";
	
		Statement stmt = connectionLattesRDB.createStatement();
        ResultSet rs = stmt.executeQuery( "SELECT curriculo_vitae_type.NUMEROIDENTIFICADOR FROM public.curriculo_vitae_type WHERE NUMEROIDENTIFICADOR = '"+id16+"';" );
        while ( rs.next() ) {
            id = rs.getString("NUMEROIDENTIFICADOR");
         }
         rs.close();
         stmt.close();
		
		return id == "" ? false : true ;
	}
	
	private void load() throws SQLException {
		Unmarshaller unmarshaller;
		int countXml = 0;
		String xml = null;
		String id16 = null;
		InputStream in = null;
		Object object = null;
		CurriculoVitaeType xmlObject = null;
		HashMap<Integer, String> xmls = getXmlIds();
		int xmlsSize = xmls.size();
		float percentage;
		String[] errors = {"id16-failed"};
		
		for(Integer id: xmls.keySet()){
			++countXml;
			percentage = (countXml*100f)/xmlsSize;
			id16 = xmls.get(id);
			if(Arrays.asList(errors).contains(id16))
				continue;
			if(hasCurriculum(id16))
				continue;
			xml = getXml(id);
			if(countXml % 100 == 0)
				System.out.println(" ! ");
			if(countXml % 500 == 0)
				System.out.println("\n"+countXml +"/"+ xmlsSize +" "+(new DecimalFormat("#0.00").format(percentage)) +"% - "+ id16);
			try {
				unmarshaller = context.createUnmarshaller();
	//			final Object object = unmarshaller.unmarshal(new File("src/test/samples/curriculum.xml"));
				in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
				object = unmarshaller.unmarshal(in);
				xmlObject = ((JAXBElement<CurriculoVitaeType>) object).getValue();
			} catch (JAXBException e) {
				e.printStackTrace();
			}
			saveManager.getTransaction().begin();
			if(xmlObject.getNUMEROIDENTIFICADOR() == null || xmlObject.getNUMEROIDENTIFICADOR().equals(""))
				xmlObject.setNUMEROIDENTIFICADOR(id16);
			saveManager.persist(xmlObject);
			saveManager.getTransaction().commit();
		}
	}
	
	private void clean() throws SQLException {
		HashMap<Integer, String> xmls = getXmlIds();
		List<String> ids = new ArrayList<String>();
		String id16 = "";
		
		for(Integer id: xmls.keySet()){
			id16 = xmls.get(id);
			if(ids.contains(id16)){
				System.out.print(" x ");
				removeCurriculum(id);
			}else{
				ids.add(id16);
			}
		}
	}
	

	public static void main(String[] args) {
		System.out.println("Iniciando o app");
		
		Date start = new Date();
		
		InsertCurriculum curriculum = new InsertCurriculum();
		try {
			curriculum.setUp();
			curriculum.load();
//			curriculum.clean();
//			System.out.println(curriculum.getXml(111));
			curriculum.tearDown();
		} catch (Exception e) {
			System.out.println("Error!!!!!!");
			e.printStackTrace();
			System.exit(0);
		}
		
		Date end = new Date();
		long diff = end.getTime() - start.getTime();
		long seconds = TimeUnit.MILLISECONDS.toSeconds(diff)%60;
		long minutes = TimeUnit.MILLISECONDS.toMinutes(diff)%60;
		long hours = TimeUnit.MILLISECONDS.toHours(diff);
		
		System.out.println("Finalizando o app em: "+hours+":"+minutes+":"+seconds);
	}
	
}
