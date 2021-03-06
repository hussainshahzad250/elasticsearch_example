package com.process;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class Main {

	private static String CLUSTER;
	private static String SEARCH_HOST;
	private static String NEW_INDEX;
	private static String NEW_TYPE;
	private static String USERNAME;
	private static String PASSWORD;
	private static String DBNAME;
	private static String DBIP;
	private static String QUERY;
	private static String GOD_QUERY;
	private static String BOOK_QUERY;
	private static String BOOK_CHAPTER_QUERY;
	private static String PRAYER_QUERY;
	private static String FEATURES_QUERY;
	private static String PILGRIMAGE_QUERY;
	private static String FESTIVAL_QUERY;
	private static String PANCHANG_QUERY;
	private static String RELIGION_QUERY;

	private static final String VARIABLE_RELIGION_ID = "VARIABLE_RELIGION_ID";
	private static final String SEPERATOR = "-";
	private static BulkRequestBuilder brb;
	private static Client POST_CLIENT;
	private static Properties prop = new Properties();
	private static final ImageDBConnections connections = new ImageDBConnections();

	private static Map<Long, MasterPojo> PRAYER_TYPE_MAP = new HashMap<Long, MasterPojo>();
	private static Map<Long, MasterPojo> FESTIVAL_TYPE_MAP = new HashMap<Long, MasterPojo>();
	private static Map<Long, String> AVATAR_MAP = new HashMap<Long, String>();
	private static Map<Long, String> GOD_ID_MAP = new HashMap<Long, String>();
	@SuppressWarnings("unused")
	private static DateFormat ES_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS'Z'");
	private static DateFormat MONTH_FORMAT = new SimpleDateFormat("MMMM");

	static {
		try {
			prop.load(new FileInputStream("config.properties"));
			CLUSTER = prop.getProperty("cluster");
			SEARCH_HOST = prop.getProperty("searchHost");
			NEW_INDEX = prop.getProperty("newIndexName");
			NEW_TYPE = prop.getProperty("newtype");
			USERNAME = prop.getProperty("username");
			PASSWORD = prop.getProperty("password");
			DBNAME = prop.getProperty("dbname");
			DBIP = prop.getProperty("dbip");
			QUERY = prop.getProperty("Query");
			GOD_QUERY = prop.getProperty("GOD_QUERY");
			BOOK_QUERY = prop.getProperty("BOOK_QUERY");
			BOOK_CHAPTER_QUERY = prop.getProperty("BOOK_CHAPTER_QUERY");
			PRAYER_QUERY = prop.getProperty("PRAYER_QUERY");
			FEATURES_QUERY = prop.getProperty("FEATURES_QUERY");
			PILGRIMAGE_QUERY = prop.getProperty("PILGRIMAGE_QUERY");
			FESTIVAL_QUERY = prop.getProperty("FESTIVAL_QUERY");
			PANCHANG_QUERY = prop.getProperty("PANCHANG_QUERY");
			RELIGION_QUERY = prop.getProperty("RELIGION_QUERY");
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	@SuppressWarnings({ "unchecked", "unused" })
	public static void main(String[] args) {

		Settings settings = Settings.builder().put("cluster.name", CLUSTER).put("client.transport.sniff", false)
				.build();
		TransportClient client = TransportClient.builder().settings(settings).build();

		try {
			POST_CLIENT = client
					.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(SEARCH_HOST), 9300));
			brb = POST_CLIENT.prepareBulk();

			Connection connection = null;
			PreparedStatement preparedStatement = null;
			ResultSet resultSet = null;
			List<JSONObject> jsonObjects = new ArrayList<JSONObject>();
			try {
				connection = connections.sqlserver(USERNAME, PASSWORD, DBIP, DBNAME);
				loadCache(connection);
				preparedStatement = connection.prepareStatement(QUERY);
				resultSet = preparedStatement.executeQuery();
				List<JSONObject> objects = new ArrayList<JSONObject>();
				JSONObject jsonObject = null;
				String religionName = null;
				PreparedStatement preparedStatement2 = null;
				ResultSet resultSet2 = null;

				while (resultSet.next()) {
					long religionId = resultSet.getLong("id");
					religionName = resultSet.getString("name");
					religionName = religionName.toLowerCase().trim();
					// First fetching Gods
					preparedStatement2 = connection
							.prepareStatement(GOD_QUERY.replaceAll(VARIABLE_RELIGION_ID, religionId + ""));
					resultSet2 = preparedStatement2.executeQuery();

					if (resultSet2 != null) {
						while (resultSet2.next()) {
							jsonObject = new JSONObject();
							fillJsonObject(jsonObject, religionName, resultSet2, "god");
							if ("Y".equalsIgnoreCase(resultSet2.getString("isgod"))) {
								GOD_ID_MAP.put(resultSet2.getLong("god_id"),
										resultSet2.getString("name").toLowerCase().replaceAll(" ", SEPERATOR));
								jsonObject.put("A4", "god");
							} else {
								jsonObject.put("A4", GOD_ID_MAP.get(resultSet2.getLong("god_id")));
							}
							System.out.println(jsonObject.get("ID"));
							System.out.println(jsonObject.get("EngTitle"));
							AVATAR_MAP.put(Long.valueOf(jsonObject.get("ID").toString()),
									jsonObject.get("EngTitle").toString());
							if ("Y".equalsIgnoreCase(resultSet2.getString("isgod"))
									&& (resultSet2.getString("hindesc1") == null
											|| resultSet2.getString("hindesc1").length() < 1)) {
								continue;
							}
							jsonObject.put("_id", religionId + "god" + jsonObject.get("ID"));
							objects.add(jsonObject);
						}
						postBulk(objects);
						objects = new ArrayList<JSONObject>();
					}
					// Books
					preparedStatement2 = connection
							.prepareStatement(BOOK_QUERY.replaceAll(VARIABLE_RELIGION_ID, religionId + ""));
					resultSet2 = preparedStatement2.executeQuery();
					if (resultSet2 != null) {
						String avatarName = null;
						while (resultSet2.next()) {
							jsonObject = new JSONObject();
							fillJsonObject(jsonObject, religionName, resultSet2, "religious-books");
							if (AVATAR_MAP.get(resultSet2.getLong("avatar_id")) != null) {
								jsonObject.put("A4", AVATAR_MAP.get(resultSet2.getLong("avatar_id")).toLowerCase()
										.replaceAll(" ", SEPERATOR));
							}

							jsonObject.put("_id", religionId + "religious-books" + jsonObject.get("ID"));
							objects.add(jsonObject);
							objects.addAll(getChapters(resultSet2.getLong("ID"), connection, religionName,
									jsonObject.get("EngTitle").toString(),
									AVATAR_MAP.get(resultSet2.getLong("avatar_id")), religionId));
						}

						postBulk(objects);
						objects = new ArrayList<JSONObject>();
					}
					// Prayers
					preparedStatement2 = connection
							.prepareStatement(PRAYER_QUERY.replaceAll(VARIABLE_RELIGION_ID, religionId + ""));
					resultSet2 = preparedStatement2.executeQuery();
					if (resultSet2 != null) {
						while (resultSet2.next()) {
							jsonObject = new JSONObject();
							fillJsonObject(jsonObject, religionName, resultSet2, "prayer");
							if (resultSet2.getLong("prayertype_id") > 0) {
								jsonObject.put("A3", PRAYER_TYPE_MAP.get(resultSet2.getLong("prayertype_id")).getName()
										.replaceAll(" ", SEPERATOR));
							}
							if (AVATAR_MAP.get(resultSet2.getLong("avatar_id")) != null) {
								jsonObject.put("A4", AVATAR_MAP.get(resultSet2.getLong("avatar_id"))
										.replaceAll(" ", SEPERATOR).toLowerCase());
							}

							jsonObject.put("_id", religionId + "prayer" + jsonObject.get("ID"));
							jsonObjects.add(jsonObject);
						}
						postBulk(jsonObjects);
						jsonObjects = new ArrayList<JSONObject>();
					}
					// Features
					preparedStatement2 = connection
							.prepareStatement(FEATURES_QUERY.replaceAll(VARIABLE_RELIGION_ID, religionId + ""));
					resultSet2 = preparedStatement2.executeQuery();
					if (resultSet2 != null) {
						while (resultSet2.next()) {
							jsonObject = new JSONObject();
							fillJsonObject(jsonObject, religionName, resultSet2, "features");
							jsonObject.put("_id", religionId + "features" + jsonObject.get("ID"));
							jsonObjects.add(jsonObject);
						}
						postBulk(jsonObjects);
						jsonObjects = new ArrayList<JSONObject>();
					}
					// Pilgrimage
					preparedStatement2 = connection
							.prepareStatement(PILGRIMAGE_QUERY.replaceAll(VARIABLE_RELIGION_ID, religionId + ""));
					resultSet2 = preparedStatement2.executeQuery();
					if (resultSet2 != null) {
						while (resultSet2.next()) {
							jsonObject = new JSONObject();

							fillJsonObject(jsonObject, religionName, resultSet2, "religious-places");
							jsonObject.put("_id", religionId + "religious-places" + jsonObject.get("ID"));
							jsonObjects.add(jsonObject);
						}
						postBulk(jsonObjects);
						jsonObjects = new ArrayList<JSONObject>();
					}
					// Festival & Vrat
					preparedStatement2 = connection
							.prepareStatement(FESTIVAL_QUERY.replaceAll(VARIABLE_RELIGION_ID, religionId + ""));
					resultSet2 = preparedStatement2.executeQuery();
					if (resultSet2 != null) {
						while (resultSet2.next()) {
							jsonObject = new JSONObject();
							fillJsonObject(jsonObject, religionName, resultSet2,
									FESTIVAL_TYPE_MAP.get(resultSet2.getLong("type_id")).getName());
							if (FESTIVAL_TYPE_MAP.get(resultSet2.getLong("type_id")) == null) {
								continue;
							}
							jsonObject.put("_id",
									religionId + FESTIVAL_TYPE_MAP.get(resultSet2.getLong("type_id")).getName()
											+ jsonObject.get("ID"));
							if (AVATAR_MAP.get(resultSet2.getLong("avatar_id")) != null) {
								jsonObject.put("A4", AVATAR_MAP.get(resultSet2.getLong("avatar_id"))
										.replaceAll(" ", SEPERATOR).toLowerCase());
							}
							jsonObjects.add(jsonObject);
						}
						postBulk(jsonObjects);
						jsonObjects = new ArrayList<JSONObject>();
					}
					// Panchang
					preparedStatement2 = connection
							.prepareStatement(PANCHANG_QUERY.replaceAll(VARIABLE_RELIGION_ID, religionId + ""));
					resultSet2 = preparedStatement2.executeQuery();
					if (resultSet2 != null) {
						Date date = null;
						DateFormat panchangDate = new SimpleDateFormat("dd/MM/yyyy");
						while (resultSet2.next()) {
							jsonObject = new JSONObject();
							date = panchangDate.parse(resultSet2.getString("date"));
							if (resultSet2.getString("name") != null
									&& (resultSet2.getString("name").toLowerCase().startsWith("vrat ")
											|| resultSet2.getString("name").toLowerCase().contains(" vrat ")
											|| resultSet2.getString("name").toLowerCase().endsWith(" vrat"))) {
								fillJsonObject(jsonObject, religionName, resultSet2, "vrat-dates");
								jsonObject.put("_id", religionId + "vrat_dates" + jsonObject.get("ID"));
								jsonObject.put("A3", resultSet2.getString("name").toLowerCase().replaceAll(" ", "-"));
							} else {
								fillJsonObject(jsonObject, religionName, resultSet2, "panchang");
								jsonObject.put("_id", religionId + "panchang" + jsonObject.get("ID"));
								jsonObject.put("A3", MONTH_FORMAT.format(date).toLowerCase());
							}
							jsonObject.put("D1", date);
							jsonObjects.add(jsonObject);
						}
					}
					// About
					preparedStatement2 = connection
							.prepareStatement(RELIGION_QUERY.replaceAll(VARIABLE_RELIGION_ID, religionId + ""));
					resultSet2 = preparedStatement2.executeQuery();
					if (resultSet2 != null) {
						while (resultSet2.next()) {
							jsonObject = new JSONObject();
							fillJsonObject(jsonObject, religionName, resultSet2, "about");
							jsonObject.put("_id", religionId + "about" + jsonObject.get("ID"));
							jsonObject.put("EngTitle", "about");
							jsonObject.put("HinTitle", "about");
							jsonObjects.add(jsonObject);
						}
					}
				}
			} catch (Exception e) {
				if (jsonObjects.size() > 0) {
					postBulk(jsonObjects);
				}
				e.printStackTrace();
			} finally {
				try {
					if (preparedStatement != null) {
						preparedStatement.close();
					}
					if (connection != null) {
						connection.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			POST_CLIENT.close();

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private static List<JSONObject> getChapters(long bookId, Connection connection, String religionName,
			String bookName, String avatarName, long religionId) throws SQLException, UnsupportedEncodingException {
		PreparedStatement preparedStatement = connection
				.prepareStatement(BOOK_CHAPTER_QUERY.replaceAll("VARIABLE_BOOK_ID", bookId + ""));
		ResultSet resultSet = preparedStatement.executeQuery();
		List<JSONObject> jsonObjects = new ArrayList<JSONObject>();
		if (resultSet != null) {
			JSONObject jsonObject = null;
			while (resultSet.next()) {
				jsonObject = new JSONObject();
				fillJsonObject(jsonObject, religionName, resultSet, "book_chapter");
				jsonObject.put("A3", bookName.replaceAll(" ", SEPERATOR).toLowerCase());
				jsonObject.put("A4", avatarName.replaceAll(" ", SEPERATOR).toLowerCase());
				jsonObject.put("_id", religionId + "book_chapter" + jsonObject.get("ID"));
				jsonObjects.add(jsonObject);
			}
			resultSet.close();
		}
		preparedStatement.close();
		return jsonObjects;
	}

	@SuppressWarnings("unchecked")
	private static void fillJsonObject(JSONObject jsonObject, String religionName, ResultSet resultSet2, String A2)
			throws SQLException, UnsupportedEncodingException {
		jsonObject.put("A1", religionName);
		jsonObject.put("A2", A2.replaceAll(" ", SEPERATOR));
		ResultSetMetaData rsMetaData = resultSet2.getMetaData();
		int numberOfColumns = rsMetaData.getColumnCount();
		Set<String> columns = new HashSet<String>();
		// get the column names; column indexes start from 1
		for (int i = 1; i < numberOfColumns + 1; i++) {
			columns.add(rsMetaData.getColumnName(i));
		}
		if (columns.contains("name") && resultSet2.getString("name") != null) {
			jsonObject.put("EngTitle", resultSet2.getString("name").trim());
		}

		if (columns.contains("hname") && resultSet2.getBlob("hname") != null) {
			jsonObject
					.put("HinTitle",
							new String(
									resultSet2.getBlob("hname").getBytes(1, (int) resultSet2.getBlob("hname").length()),
									"UTF-8").trim());
			if (hasRoman(jsonObject.get("HinTitle").toString())) {
				jsonObject.put("HinTitleFlag", 0);
			} else {
				jsonObject.put("HinTitleFlag", 1);
			}
		} else {
			jsonObject.put("HinTitleFlag", 0);
		}
		if (jsonObject.get("HinTitle") == null || jsonObject.get("HinTitle").toString().trim().length() < 1) {
			jsonObject.put("HinTitleFlag", 0);
		}
		if (columns.contains("hindesc1") && resultSet2.getBlob("hindesc1") != null) {
			String desc = new String(
					resultSet2.getBlob("hindesc1").getBytes(1, (int) resultSet2.getBlob("hindesc1").length()), "UTF-8")
							.trim();
			desc = desc.replaceAll("<p>&nbsp;</p>", "");
			desc = desc.trim();
			jsonObject.put("HinDesc", desc);
		}
		if (columns.contains("imagelink") && resultSet2.getBlob("imagelink") != null) {
			jsonObject.put("ImgSrc",
					new String(
							resultSet2.getBlob("imagelink").getBytes(1, (int) resultSet2.getBlob("imagelink").length()),
							"UTF-8").trim());
		}
		if (columns.contains("imagesource") && resultSet2.getBlob("imagesource") != null) {
			jsonObject.put("ImgSrc", new String(
					resultSet2.getBlob("imagesource").getBytes(1, (int) resultSet2.getBlob("imagesource").length()),
					"UTF-8").trim());
		}
		if (columns.contains("externallink") && resultSet2.getBlob("externallink") != null) {
			jsonObject.put("HinTitleLink", new String(
					resultSet2.getBlob("externallink").getBytes(1, (int) resultSet2.getBlob("externallink").length()),
					"UTF-8").trim());
		}
		int rank = 0;
		if (columns.contains("serialno")) {
			rank = resultSet2.getInt("serialno");
		}
		if (rank == 0) {
			jsonObject.put("Rank", 999);
		} else {
			jsonObject.put("Rank", rank);
		}
		/*
		 * if(columns.contains("stampat") && resultSet2.getTimestamp("stampat")
		 * != null){ jsonObject.put("D1", resultSet2.getTimestamp("stampat")); }
		 * else{ jsonObject.put("D1", new Date()); }
		 */
		jsonObject.put("D1", new Date());
		jsonObject.put("ID", resultSet2.getInt("id"));
	}

	private static void loadCache(Connection connection) throws SQLException, UnsupportedEncodingException {
		PreparedStatement preparedStatement = connection.prepareStatement(prop.getProperty("CACHE_QUERY_1"));
		ResultSet resultSet = preparedStatement.executeQuery();
		if (resultSet != null) {
			@SuppressWarnings("unused")
			JSONArray array = new JSONArray();
			MasterPojo masterPojo = null;
			while (resultSet.next()) {
				masterPojo = new MasterPojo();
				if (resultSet.getBlob("hname") != null) {
					masterPojo.sethName(new String(
							resultSet.getBlob("hname").getBytes(1, (int) resultSet.getBlob("hname").length()),
							"UTF-8"));
				}
				masterPojo.setName(resultSet.getString("name").toLowerCase());
				masterPojo.setParentId(resultSet.getLong("religion_id"));
				PRAYER_TYPE_MAP.put(resultSet.getLong("id"), masterPojo);
			}
		}
		preparedStatement = connection.prepareStatement(prop.getProperty("CACHE_QUERY_2"));
		resultSet = preparedStatement.executeQuery();
		if (resultSet != null) {
			@SuppressWarnings("unused")
			JSONArray array = new JSONArray();
			MasterPojo masterPojo = null;
			while (resultSet.next()) {
				masterPojo = new MasterPojo();
				if (resultSet.getBlob("hname") != null) {
					masterPojo.sethName(new String(
							resultSet.getBlob("hname").getBytes(1, (int) resultSet.getBlob("hname").length()),
							"UTF-8"));
				}
				masterPojo.setName(resultSet.getString("name").toLowerCase());
				masterPojo.setParentId(resultSet.getLong("parentid"));
				FESTIVAL_TYPE_MAP.put(resultSet.getLong("id"), masterPojo);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void postBulk(List<JSONObject> jsonObjects) {
		if (jsonObjects.size() < 1) {
			return;
		}
		String id = null;
		for (JSONObject jsonObject : jsonObjects) {
			id = jsonObject.get("_id").toString();
			jsonObject.remove("_id");
			brb.add(POST_CLIENT.prepareIndex(NEW_INDEX, NEW_TYPE).setSource(jsonObject).setId(id));
		}
		try {
			BulkResponse br = (BulkResponse) brb.execute().actionGet();
			if (br.hasFailures()) {
				System.out.println(br.buildFailureMessage());
			} else {
				System.out.println("Bulk posted");
			}
			brb = null;
			brb = POST_CLIENT.prepareBulk();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private static boolean hasRoman(String string) {
		if (string.matches("^[\\u0900-\\u097F].*")) {
			return false;
		}
		return true;
	}
}
