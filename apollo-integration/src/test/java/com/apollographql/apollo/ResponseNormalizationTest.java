package com.apollographql.apollo;

import android.support.annotation.NonNull;

import com.apollographql.android.impl.normalizer.EpisodeHeroName;
import com.apollographql.android.impl.normalizer.HeroAndFriendsNames;
import com.apollographql.android.impl.normalizer.HeroAndFriendsNamesWithIDForParentOnly;
import com.apollographql.android.impl.normalizer.HeroAndFriendsNamesWithIDs;
import com.apollographql.android.impl.normalizer.HeroAppearsIn;
import com.apollographql.android.impl.normalizer.HeroName;
import com.apollographql.android.impl.normalizer.HeroParentTypeDependentField;
import com.apollographql.android.impl.normalizer.HeroTypeDependentAliasedField;
import com.apollographql.android.impl.normalizer.SameHeroTwice;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.cache.normalized.CacheKey;
import com.apollographql.apollo.cache.normalized.CacheKeyResolver;
import com.apollographql.apollo.cache.normalized.CacheReference;
import com.apollographql.apollo.cache.normalized.NormalizedCache;
import com.apollographql.apollo.cache.normalized.Record;
import com.apollographql.apollo.exception.ApolloException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static com.apollographql.android.impl.normalizer.type.Episode.EMPIRE;
import static com.apollographql.android.impl.normalizer.type.Episode.JEDI;
import static com.google.common.truth.Truth.assertThat;

public class ResponseNormalizationTest {

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

  private ApolloClient apolloClient;
  private MockWebServer server;
  private NormalizedCache normalizedCache;

  private final String QUERY_ROOT_KEY = "QUERY_ROOT";

  @Before public void setUp() {
    server = new MockWebServer();
    OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
    InMemoryNormalizedCache inMemoryNormalizedCache = new InMemoryNormalizedCache();

    apolloClient = ApolloClient.builder()
        .serverUrl(server.url("/"))
        .okHttpClient(okHttpClient)
        .normalizedCache(inMemoryNormalizedCache, new CacheKeyResolver<Map<String, Object>>() {
          @Nonnull @Override public CacheKey resolve(@NonNull Map<String, Object> jsonObject) {
            String id = (String) jsonObject.get("id");
            if (id == null || id.isEmpty()) {
              return CacheKey.NO_KEY;
            }
            return CacheKey.from(id);
          }
        })
        .dispatcher(Utils.immediateExecutorService())
        .build();
    normalizedCache = apolloClient.apolloStore().normalizedCache();
  }

  @After public void tearDown() {
    try {
      server.shutdown();
    } catch (IOException ignored) {
    }
  }

  private MockResponse mockResponse(String fileName) throws IOException, ApolloException {
    return new MockResponse().setChunkedBody(Utils.readFileToString(getClass(), "/" + fileName), 32);
  }

  @Test public void testHeroName() throws IOException, ApolloException {
    MockResponse mockResponse = mockResponse("HeroNameResponse.json");
    server.enqueue(mockResponse);

    ApolloCall<HeroName.Data> call = apolloClient.newCall(new HeroName());
    Response<HeroName.Data> body = call.execute();
    assertThat(body.hasErrors()).isFalse();

    Record record = normalizedCache.loadRecord(QUERY_ROOT_KEY);
    CacheReference reference = (CacheReference) record.field("hero");
    assertThat(reference).isEqualTo(new CacheReference("hero"));

    final Record heroRecord = normalizedCache.loadRecord(reference.key());
    assertThat(heroRecord.field("name")).isEqualTo("R2-D2");
  }

  @Test
  public void testHeroNameWithVariable() throws IOException, ApolloException {
    MockResponse mockResponse = mockResponse("EpisodeHeroNameResponse.json");
    server.enqueue(mockResponse);

    final EpisodeHeroName query = EpisodeHeroName.builder().episode(JEDI).build();
    ApolloCall<EpisodeHeroName.Data> call = apolloClient.newCall(query);
    Response<EpisodeHeroName.Data> body = call.execute();
    assertThat(body.hasErrors()).isFalse();

    Record record = normalizedCache.loadRecord(QUERY_ROOT_KEY);
    CacheReference reference = (CacheReference) record.field("hero(episode:JEDI)");
    assertThat(reference).isEqualTo(new CacheReference("hero(episode:JEDI)"));

    final Record heroRecord = normalizedCache.loadRecord(reference.key());
    assertThat(heroRecord.field("name")).isEqualTo("R2-D2");
  }


  @Test
  public void testHeroAppearsInQuery() throws IOException, ApolloException {
    MockResponse mockResponse = mockResponse("HeroAppearsInResponse.json");
    server.enqueue(mockResponse);

    final HeroAppearsIn heroAppearsInQuery = new HeroAppearsIn();

    ApolloCall<HeroAppearsIn.Data> call = apolloClient.newCall(heroAppearsInQuery);
    Response<HeroAppearsIn.Data> body = call.execute();
    assertThat(body.hasErrors()).isFalse();

    Record record = normalizedCache.loadRecord(QUERY_ROOT_KEY);
    CacheReference heroReference = (CacheReference) record.field("hero");
    assertThat(heroReference).isEqualTo(new CacheReference("hero"));

    final Record hero = normalizedCache.loadRecord(heroReference.key());
    assertThat(hero.field("appearsIn")).isEqualTo(Arrays.asList("NEWHOPE", "EMPIRE", "JEDI"));
  }

  @Test
  public void testHeroAndFriendsNamesQueryWithoutIDs() throws IOException, ApolloException {
    MockResponse mockResponse = mockResponse("HeroAndFriendsNameResponse.json");
    server.enqueue(mockResponse);
    final HeroAndFriendsNames heroAndFriendsNameQuery = HeroAndFriendsNames.builder().episode(JEDI).build();

    ApolloCall<HeroAndFriendsNames.Data> call = apolloClient.newCall(heroAndFriendsNameQuery);
    Response<HeroAndFriendsNames.Data> body = call.execute();
    assertThat(body.hasErrors()).isFalse();

    Record record = normalizedCache.loadRecord(QUERY_ROOT_KEY);
    CacheReference heroReference = (CacheReference) record.field("hero(episode:JEDI)");
    assertThat(heroReference).isEqualTo(new CacheReference("hero(episode:JEDI)"));

    final Record heroRecord = normalizedCache.loadRecord(heroReference.key());
    assertThat(heroRecord.field("name")).isEqualTo("R2-D2");

    assertThat(heroRecord.field("friends")).isEqualTo(Arrays.asList(
        new CacheReference("hero(episode:JEDI).friends.0"),
        new CacheReference("hero(episode:JEDI).friends.1"),
        new CacheReference("hero(episode:JEDI).friends.2")
    ));

    final Record luke = normalizedCache.loadRecord("hero(episode:JEDI).friends.0");
    assertThat(luke.field("name")).isEqualTo("Luke Skywalker");
  }

  @Test
  public void testHeroAndFriendsNamesQueryWithIDs() throws IOException, ApolloException {
    MockResponse mockResponse = mockResponse("HeroAndFriendsNameWithIdsResponse.json");
    server.enqueue(mockResponse);
    final HeroAndFriendsNamesWithIDs heroAndFriendsWithIdsQuery = HeroAndFriendsNamesWithIDs.builder().episode(JEDI).build();

    ApolloCall<HeroAndFriendsNamesWithIDs.Data> call = apolloClient.newCall(heroAndFriendsWithIdsQuery);
    Response<HeroAndFriendsNamesWithIDs.Data> body = call.execute();
    assertThat(body.hasErrors()).isFalse();

    Record record = normalizedCache.loadRecord(QUERY_ROOT_KEY);
    CacheReference heroReference = (CacheReference) record.field("hero(episode:JEDI)");
    assertThat(heroReference).isEqualTo(new CacheReference("2001"));

    final Record heroRecord = normalizedCache.loadRecord(heroReference.key());
    assertThat(heroRecord.field("name")).isEqualTo("R2-D2");

    assertThat(heroRecord.field("friends")).isEqualTo(Arrays.asList(
        new CacheReference("1000"),
        new CacheReference("1002"),
        new CacheReference("1003")
    ));

    final Record luke = normalizedCache.loadRecord("1000");
    assertThat(luke.field("name")).isEqualTo("Luke Skywalker");
  }

  @Test
  public void testHeroAndFriendsNamesWithIDForParentOnly() throws IOException, ApolloException {
    MockResponse mockResponse = mockResponse("HeroAndFriendsNameWithIdsParentOnlyResponse.json");
    server.enqueue(mockResponse);
    final HeroAndFriendsNamesWithIDForParentOnly heroAndFriendsWithIdsQuery = HeroAndFriendsNamesWithIDForParentOnly
        .builder().episode(JEDI).build();

    ApolloCall<HeroAndFriendsNamesWithIDForParentOnly.Data> call = apolloClient.newCall(heroAndFriendsWithIdsQuery);
    Response<HeroAndFriendsNamesWithIDForParentOnly.Data> body = call.execute();
    assertThat(body.hasErrors()).isFalse();

    Record record = normalizedCache.loadRecord(QUERY_ROOT_KEY);
    CacheReference heroReference = (CacheReference) record.field("hero(episode:JEDI)");
    assertThat(heroReference).isEqualTo(new CacheReference("2001"));

    final Record heroRecord = normalizedCache.loadRecord(heroReference.key());
    assertThat(heroRecord.field("name")).isEqualTo("R2-D2");

    assertThat(heroRecord.field("friends")).isEqualTo(Arrays.asList(
        new CacheReference("2001.friends.0"),
        new CacheReference("2001.friends.1"),
        new CacheReference("2001.friends.2")
    ));

    final Record luke = normalizedCache.loadRecord("2001.friends.0");
    assertThat(luke.field("name")).isEqualTo("Luke Skywalker");
  }

  @Test
  public void testSameHeroTwiceQuery() throws IOException, ApolloException {
    MockResponse mockResponse = mockResponse("SameHeroTwiceResponse.json");
    server.enqueue(mockResponse);

    final SameHeroTwice sameHeroTwiceQuery = new SameHeroTwice();

    ApolloCall<SameHeroTwice.Data> call = apolloClient.newCall(sameHeroTwiceQuery);
    Response<SameHeroTwice.Data> body = call.execute();
    assertThat(body.hasErrors()).isFalse();

    Record record = normalizedCache.loadRecord(QUERY_ROOT_KEY);
    CacheReference heroReference = (CacheReference) record.field("hero");

    final Record hero = normalizedCache.loadRecord(heroReference.key());
    assertThat(hero.field("name")).isEqualTo("R2-D2");
    assertThat(hero.field("appearsIn")).isEqualTo(Arrays.asList("NEWHOPE", "EMPIRE", "JEDI"));
  }

  @Test
  public void testHeroTypeDependentAliasedFieldQueryDroid() throws IOException, ApolloException {
    MockResponse mockResponse = mockResponse("HeroTypeDependentAliasedFieldResponse.json");
    server.enqueue(mockResponse);

    final HeroTypeDependentAliasedField aliasedQuery = HeroTypeDependentAliasedField.builder().episode(JEDI).build();

    ApolloCall<HeroTypeDependentAliasedField.Data> call = apolloClient.newCall(aliasedQuery);
    Response<HeroTypeDependentAliasedField.Data> body = call.execute();
    assertThat(body.hasErrors()).isFalse();

    Record record = normalizedCache.loadRecord(QUERY_ROOT_KEY);
    CacheReference heroReference = (CacheReference) record.field("hero(episode:JEDI)");

    final Record hero = normalizedCache.loadRecord(heroReference.key());
    assertThat(hero.field("primaryFunction")).isEqualTo("Astromech");
    assertThat(hero.field("__typename")).isEqualTo("Droid");
  }

  @Test
  public void testHeroTypeDependentAliasedFieldQueryHuman() throws IOException, ApolloException {
    MockResponse mockResponse = mockResponse("HeroTypeDependentAliasedFieldResponseHuman.json");
    server.enqueue(mockResponse);

    final HeroTypeDependentAliasedField aliasedQuery = HeroTypeDependentAliasedField.builder().episode(EMPIRE).build();

    ApolloCall<HeroTypeDependentAliasedField.Data> call = apolloClient.newCall(aliasedQuery);
    Response<HeroTypeDependentAliasedField.Data> body = call.execute();
    assertThat(body.hasErrors()).isFalse();

    Record record = normalizedCache.loadRecord(QUERY_ROOT_KEY);
    CacheReference heroReference = (CacheReference) record.field("hero(episode:EMPIRE)");

    final Record hero = normalizedCache.loadRecord(heroReference.key());
    assertThat(hero.field("homePlanet")).isEqualTo("Tatooine");
    assertThat(hero.field("__typename")).isEqualTo("Human");
  }

  @Test
  public void testHeroParentTypeDependentAliasedFieldQueryHuman() throws IOException, ApolloException {
    MockResponse mockResponse = mockResponse("HeroTypeDependentAliasedFieldResponseHuman.json");
    server.enqueue(mockResponse);

    final HeroTypeDependentAliasedField aliasedQuery = HeroTypeDependentAliasedField.builder().episode(EMPIRE).build();

    ApolloCall<HeroTypeDependentAliasedField.Data> call = apolloClient.newCall(aliasedQuery);
    Response<HeroTypeDependentAliasedField.Data> body = call.execute();
    assertThat(body.hasErrors()).isFalse();

    Record record = normalizedCache.loadRecord(QUERY_ROOT_KEY);
    CacheReference heroReference = (CacheReference) record.field("hero(episode:EMPIRE)");

    final Record hero = normalizedCache.loadRecord(heroReference.key());
    assertThat(hero.field("homePlanet")).isEqualTo("Tatooine");
    assertThat(hero.field("__typename")).isEqualTo("Human");
  }

  @Test
  public void testHeroParentTypeDependentFieldDroid() throws IOException, ApolloException {
    MockResponse mockResponse = mockResponse("HeroParentTypeDependentFieldDroidResponse.json");
    server.enqueue(mockResponse);
    final HeroParentTypeDependentField aliasedQuery = HeroParentTypeDependentField.builder().episode(JEDI).build();

    ApolloCall<HeroParentTypeDependentField.Data> call = apolloClient.newCall(aliasedQuery);
    Response<HeroParentTypeDependentField.Data> body = call.execute();
    assertThat(body.hasErrors()).isFalse();

    Record lukeRecord = normalizedCache.loadRecord("hero(episode:JEDI).friends.0");
    assertThat(lukeRecord.field("name")).isEqualTo("Luke Skywalker");
    assertThat(lukeRecord.field("height(unit:METER)")).isEqualTo(BigDecimal.valueOf(1.72));

    final List<Object> friends = (List<Object>) normalizedCache.loadRecord("hero(episode:JEDI)").field("friends");
    assertThat(friends.get(0)).isEqualTo(new CacheReference("hero(episode:JEDI).friends.0"));
    assertThat(friends.get(1)).isEqualTo(new CacheReference("hero(episode:JEDI).friends.1"));
    assertThat(friends.get(2)).isEqualTo(new CacheReference("hero(episode:JEDI).friends.2"));
  }

  @Test
  public void testHeroParentTypeDependentFieldHuman() throws IOException, ApolloException {
    MockResponse mockResponse = mockResponse("HeroParentTypeDependentFieldHumanResponse.json");
    server.enqueue(mockResponse);
    final HeroParentTypeDependentField aliasedQuery = HeroParentTypeDependentField.builder().episode(EMPIRE).build();

    ApolloCall<HeroParentTypeDependentField.Data> call = apolloClient.newCall(aliasedQuery);
    Response<HeroParentTypeDependentField.Data> body = call.execute();
    assertThat(body.hasErrors()).isFalse();

    Record lukeRecord = normalizedCache.loadRecord("hero(episode:EMPIRE).friends.0");
    assertThat(lukeRecord.field("name")).isEqualTo("Han Solo");
    assertThat(lukeRecord.field("height(unit:FOOT)")).isEqualTo(BigDecimal.valueOf(5.905512));
  }

}
