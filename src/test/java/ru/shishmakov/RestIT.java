package ru.shishmakov;

import com.jayway.restassured.RestAssured;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import ru.shishmakov.blog.Whisky;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * Integration tests for vert.x instance
 */
public class RestIT {

    @BeforeClass
    public static void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = Integer.getInteger("http.port", 8080);
    }

    @AfterClass
    public static void tearDown() {
        RestAssured.reset();
    }

    @Test
    public void checkGetShouldRetrieveOneWhisky() {
        // get list of whiskies and get id one of them
        int whiskyId = RestAssured.get("/api/whiskies").then()
                .assertThat()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getInt("find { it.name=='Bowmore 15 Years Laimrig' }.id");
        // get whisky by id
        RestAssured.get("/api/whiskies/" + whiskyId).then()
                .assertThat()
                .statusCode(200)
                .body("id", equalTo(whiskyId))
                .body("name", equalTo("Bowmore 15 Years Laimrig"))
                .body("origin", equalTo("Scotland, Islay"));

    }

    @Test
    public void checkPostShouldAddWhiskyAndDeleteShouldRemoveWhisky() {
        // post new whisky
        Whisky whisky = RestAssured.given()
                .body("{\"name\":\"Jameson\", \"origin\":\"Ireland\"}")
                .request().post("/api/whiskies")
                .thenReturn()
                .as(Whisky.class);
        assertThat(whisky.getName()).isEqualToIgnoringCase("Jameson");
        assertThat(whisky.getOrigin()).isEqualToIgnoringCase("Ireland");
        assertThat(whisky.getId()).isGreaterThan(0);
        // get posted whisky
        RestAssured.get("/api/whiskies/" + whisky.getId()).then()
                .assertThat()
                .statusCode(200)
                .body("id", equalTo(whisky.getId()))
                .body("name", equalTo(whisky.getName()))
                .body("origin", equalTo(whisky.getOrigin()));
        // delete posted whisky
        RestAssured.delete("/api/whiskies/" + whisky.getId()).then()
                .assertThat()
                .statusCode(204);
        // get removed whisky
        RestAssured.get("/api/whiskies/" + whisky.getId()).then()
                .assertThat()
                .statusCode(404);
    }
}
