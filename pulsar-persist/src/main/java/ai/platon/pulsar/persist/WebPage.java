/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package ai.platon.pulsar.persist;

import ai.platon.pulsar.common.AppContext;
import ai.platon.pulsar.common.DateTimes;
import ai.platon.pulsar.common.HtmlIntegrity;
import ai.platon.pulsar.common.Strings;
import ai.platon.pulsar.common.Urls;
import ai.platon.pulsar.common.config.MutableConfig;
import ai.platon.pulsar.common.config.VolatileConfig;
import ai.platon.pulsar.persist.gora.generated.GHypeLink;
import ai.platon.pulsar.persist.gora.generated.GParseStatus;
import ai.platon.pulsar.persist.gora.generated.GProtocolStatus;
import ai.platon.pulsar.persist.gora.generated.GWebPage;
import ai.platon.pulsar.persist.metadata.BrowserType;
import ai.platon.pulsar.persist.metadata.FetchMode;
import ai.platon.pulsar.persist.metadata.Mark;
import ai.platon.pulsar.persist.metadata.Name;
import ai.platon.pulsar.persist.metadata.PageCategory;
import ai.platon.pulsar.persist.model.ActiveDomMultiStatus;
import ai.platon.pulsar.persist.model.ActiveDomUrls;
import ai.platon.pulsar.persist.model.PageModel;
import ai.platon.pulsar.persist.model.WebPageFormatter;
import org.apache.avro.util.Utf8;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.gora.util.ByteUtils;
import org.apache.hadoop.hbase.util.Bytes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.platon.pulsar.common.config.AppConstants.*;

/**
 * The core data structure across the whole program execution
 * <p>
 * Notice: Use a build-in java string or a Utf8 to serialize strings?
 *
 * see org .apache .gora. hbase. util .HBaseByteInterface #fromBytes
 * <p>
 * In serializetion phrase, a byte array created by s.getBytes(UTF8_CHARSET) is serialized, and
 * in deserialization phrase, every string are wrapped to be a Utf8
 * <p>
 * So both build-in string and a Utf8 wrap is OK to serialize, and Utf8 is always returned
 */
public class WebPage implements Comparable<WebPage> {

    public static final Logger LOG = LoggerFactory.getLogger(WebPage.class);

    public static AtomicInteger sequencer = new AtomicInteger();
    public static WebPage NIL = newInternalPage(NIL_PAGE_URL, 0, "nil", "nil");

    /**
     * The process scope WebPage instance sequence
     */
    private Integer id = sequencer.incrementAndGet();
    /**
     * The url is the permanent internal address, and the location is the last working address
     */
    private String url = "";
    /**
     * The reversed url of the web page, it's also the key of the underlying storage of this object
     */
    private String reversedUrl = "";
    /**
     * Underlying persistent object
     */
    private GWebPage page;
    /**
     * Web page scope configuration
     */
    private VolatileConfig volatileConfig;
    /**
     * Web page scope variables
     * TODO : we may use it a PageDatum to track all context scope variables
     */
    private Variables variables = new Variables();

    private ByteBuffer cachedContent = null;

    private WebPage(String url, GWebPage page, boolean urlReversed) {
        Objects.requireNonNull(url);
        Objects.requireNonNull(page);
        this.url = urlReversed ? Urls.unreverseUrl(url) : url;
        this.reversedUrl = urlReversed ? url : Urls.reverseUrlOrEmpty(url);
        this.page = page;

        // the url of a page might be normalized, but the baseUrl always keeps be the original
        if (page.getBaseUrl() == null) {
            setLocation(url);
        }
    }

    private WebPage(String url, String reversedUrl, GWebPage page) {
        this.url = Objects.requireNonNull(url);
        this.reversedUrl = Objects.requireNonNull(reversedUrl);
        this.page = Objects.requireNonNull(page);

        // BaseUrl is the last working address, it might redirect to url, or it might have random parameters
        if (page.getBaseUrl() == null) {
            setLocation(url);
        }
    }

    @NotNull
    public static WebPage newWebPage(String originalUrl) {
        return newWebPage(originalUrl, false);
    }

    @NotNull
    public static WebPage newWebPage(@NotNull String originalUrl, @NotNull VolatileConfig volatileConfig) {
        return newWebPageInternal(originalUrl, volatileConfig);
    }

    @NotNull
    public static WebPage newWebPage(@NotNull String originalUrl, boolean shortenKey) {
        String url = shortenKey ? Urls.normalize(originalUrl, shortenKey) : originalUrl;
        return newWebPageInternal(url, null);
    }

    @NotNull
    public static WebPage newWebPage(@NotNull String originalUrl, boolean shortenKey, @Nullable VolatileConfig volatileConfig) {
        String url = shortenKey ? Urls.normalize(originalUrl, shortenKey) : originalUrl;
        return newWebPageInternal(url, volatileConfig);
    }

    @NotNull
    private static WebPage newWebPageInternal(@NotNull String url, @Nullable VolatileConfig volatileConfig) {
        WebPage page = new WebPage(url, GWebPage.newBuilder().build(), false);

        page.setLocation(url);
        page.setVolatileConfig(volatileConfig);
        page.setCrawlStatus(CrawlStatus.STATUS_UNFETCHED);
        page.setCreateTime(Instant.now());
        page.setScore(0);
        page.setFetchCount(0);

        return page;
    }

    @NotNull
    public static WebPage newInternalPage(@NotNull String url) {
        return newInternalPage(url, "internal", "internal");
    }

    @NotNull
    public static WebPage newInternalPage(@NotNull String url, @NotNull String title) {
        return newInternalPage(url, title, "internal");
    }

    @NotNull
    public static WebPage newInternalPage(@NotNull String url, @NotNull String title, @NotNull String content) {
        return newInternalPage(url, -1, title, content);
    }

    @NotNull
    public static WebPage newInternalPage(@NotNull String url, int id, @NotNull String title, @NotNull String content) {
        WebPage page = WebPage.newWebPage(url, false);
        if (id >= 0) {
            page.id = id;
        }

        page.setLocation(url);
        page.setModifiedTime(Instant.EPOCH);
        page.setPrevFetchTime(Instant.EPOCH);
        page.setFetchTime(Instant.parse("3000-01-01T00:00:00Z"));
        page.setFetchInterval(ChronoUnit.CENTURIES.getDuration());
        page.setFetchPriority(FETCH_PRIORITY_MIN);
        page.setCrawlStatus(CrawlStatus.STATUS_UNFETCHED);

        page.setDistance(DISTANCE_INFINITE); // or -1?
        page.getMarks().put(Mark.INTERNAL, YES_STRING);
        page.getMarks().put(Mark.INACTIVE, YES_STRING);

        page.setPageTitle(title);
        page.setContent(content);

        return page;
    }

    /**
     * Initialize a WebPage with the underlying GWebPage instance.
     */
    @NotNull
    public static WebPage box(@NotNull String url, @NotNull String reversedUrl, @NotNull GWebPage page) {
        return new WebPage(url, reversedUrl, page);
    }

    /**
     * Initialize a WebPage with the underlying GWebPage instance.
     */
    @NotNull
    public static WebPage box(@NotNull String url, @NotNull GWebPage page) {
        return new WebPage(url, page, false);
    }

    /**
     * Initialize a WebPage with the underlying GWebPage instance.
     */
    @NotNull
    public static WebPage box(@NotNull String url, @NotNull GWebPage page, boolean urlReversed) {
        return new WebPage(url, page, urlReversed);
    }

    /********************************************************************************
     * Other
     ********************************************************************************/

    @NotNull
    public static Utf8 wrapKey(@NotNull Mark mark) {
        return u8(mark.value());
    }

    /**
     * What's the difference between String and Utf8?
     */
    @Nullable
    public static Utf8 u8(@Nullable String value) {
        if (value == null) {
            // TODO: return new Utf8.EMPTY?
            return null;
        }
        return new Utf8(value);
    }

    /**
     * page.location is the last working address, and page.url is the permanent internal address
     * */
    @NotNull
    public String getUrl() {
        return url;
    }

    @NotNull
    public String getKey() {
        return getReversedUrl();
    }

    @NotNull
    public String getReversedUrl() {
        return reversedUrl != null ? reversedUrl : "";
    }

    public int getId() {
        return id;
    }

    public boolean isNil() {
        return this == NIL;
    }

    public boolean isNotNil() {
        return !isNil();
    }

    public boolean isInternal() {
        return hasMark(Mark.INTERNAL);
    }

    public boolean isNotInternal() {
        return !isInternal();
    }

    @NotNull
    public GWebPage unbox() {
        return page;
    }

    /********************************************************************************
     * Common fields
     ********************************************************************************/

    @NotNull
    public Variables getVariables() {
        return variables;
    }

    public boolean hasVar(@NotNull String name) {
        return variables.contains(name);
    }

    public boolean getAndRemoveVar(@NotNull String name) {
        boolean exist = variables.contains(name);
        if (exist) {
            variables.remove(name);
        }
        return exist;
    }

    @Nullable
    public VolatileConfig getVolatileConfig() {
        return volatileConfig;
    }

    public void setVolatileConfig(VolatileConfig volatileConfig) {
        this.volatileConfig = volatileConfig;
    }

    @NotNull
    public MutableConfig getMutableConfigOrElse(MutableConfig fallbackConfig) {
        return volatileConfig != null ? volatileConfig : fallbackConfig;
    }

    public Metadata getMetadata() {
        return Metadata.box(page.getMetadata());
    }

    /********************************************************************************
     * Creation fields
     ********************************************************************************/

    public CrawlMarks getMarks() {
        return CrawlMarks.box(page.getMarkers());
    }

    public boolean hasMark(Mark mark) {
        return page.getMarkers().get(wrapKey(mark)) != null;
    }

    /**
     * All options are saved here, including crawl options, link options, entity options and so on
     */
    public String getOptions() {
        return page.getOptions() == null ? "" : page.getOptions().toString();
    }

    public void setOptions(String options) {
        page.setOptions(options);
    }

    public String getConfiguredUrl() {
        String configuredUrl = url;
        if (page.getOptions() != null) {
            configuredUrl += " " + page.getOptions().toString();
        }
        return configuredUrl;
    }

    @Nullable
    public String getQuery() {
        return getMetadata().get(Name.QUERY);
    }

    public void setQuery(@Nullable String query) {
        getMetadata().set(Name.QUERY, query);
    }

    @NotNull
    public ZoneId getZoneId() {
        return page.getZoneId() == null ? AppContext.INSTANCE.getDefaultZoneId() : ZoneId.of(page.getZoneId().toString());
    }

    public void setZoneId(@NotNull ZoneId zoneId) {
        page.setZoneId(zoneId.getId());
    }

    public String getBatchId() {
        return page.getBatchId() == null ? "" : page.getBatchId().toString();
    }

    public void setBatchId(String value) {
        page.setBatchId(value);
    }

    public void markSeed() {
        getMetadata().set(Name.IS_SEED, YES_STRING);
    }

    public void unmarkSeed() {
        getMetadata().remove(Name.IS_SEED);
    }

    public boolean isSeed() {
        return getMetadata().contains(Name.IS_SEED);
    }

    public int getDistance() {
        int distance = page.getDistance();
        return distance < 0 ? DISTANCE_INFINITE : distance;
    }

    public void setDistance(int newDistance) {
        page.setDistance(newDistance);
    }

    public void updateDistance(int newDistance) {
        int oldDistance = getDistance();
        if (newDistance < oldDistance) {
            setDistance(newDistance);
        }
    }

    /**
     * Fetch mode is used to determine the protocol before fetch
     */
    @NotNull
    public FetchMode getFetchMode() {
        return FetchMode.fromString(getMetadata().get(Name.FETCH_MODE));
    }

    /**
     * Fetch mode is used to determine the protocol before fetch, so it shall be set before fetch
     */
    public void setFetchMode(@NotNull FetchMode mode) {
        getMetadata().set(Name.FETCH_MODE, mode.name());
    }

    @NotNull
    public BrowserType getLastBrowser() {
        return BrowserType.fromString(getMetadata().get(Name.BROWSER));
    }

    public void setLastBrowser(@NotNull BrowserType browser) {
        getMetadata().set(Name.BROWSER, browser.name());
    }

    @NotNull
    public HtmlIntegrity getHtmlIntegrity() {
        return HtmlIntegrity.Companion.fromString(getMetadata().get(Name.HTML_INTEGRITY));
    }

    public void setHtmlIntegrity(@NotNull HtmlIntegrity integrity) {
        getMetadata().set(Name.HTML_INTEGRITY, integrity.name());
    }

    public int getFetchPriority() {
        return page.getFetchPriority() > 0 ? page.getFetchPriority() : FETCH_PRIORITY_DEFAULT;
    }

    public void setFetchPriority(int priority) {
        page.setFetchPriority(priority);
    }

    public int sniffFetchPriority() {
        int priority = getFetchPriority();

        int depth = getDistance();
        if (depth < FETCH_PRIORITY_DEPTH_BASE) {
            priority = Math.max(priority, FETCH_PRIORITY_DEPTH_BASE - depth);
        }

        return priority;
    }

    @NotNull
    public Instant getCreateTime() {
        return Instant.ofEpochMilli(page.getCreateTime());
    }

    public void setCreateTime(@NotNull Instant createTime) {
        page.setCreateTime(createTime.toEpochMilli());
    }

    @NotNull
    public Instant getGenerateTime() {
        String generateTime = getMetadata().get(Name.GENERATE_TIME);
        if (generateTime == null) {
            return Instant.EPOCH;
        } else if (NumberUtils.isDigits(generateTime)) {
            // Old version of generate time, created by String.valueOf(epochMillis)
            return Instant.ofEpochMilli(NumberUtils.toLong(generateTime, 0));
        } else {
            return Instant.parse(generateTime);
        }
    }

    public void setGenerateTime(@NotNull Instant generateTime) {
        getMetadata().set(Name.GENERATE_TIME, generateTime.toString());
    }

    /********************************************************************************
     * Fetch fields
     ********************************************************************************/

    public int getFetchCount() {
        return page.getFetchCount();
    }

    public void setFetchCount(int count) {
        page.setFetchCount(count);
    }

    public void increaseFetchCount() {
        int count = getFetchCount();
        setFetchCount(count + 1);
    }

    @NotNull
    public CrawlStatus getCrawlStatus() {
        return new CrawlStatus(page.getCrawlStatus().byteValue());
    }

    public void setCrawlStatus(@NotNull CrawlStatus crawlStatus) {
        page.setCrawlStatus(crawlStatus.getCode());
    }

    /**
     * Set crawl status
     *
     * @see CrawlStatus
     */
    public void setCrawlStatus(int value) {
        page.setCrawlStatus(value);
    }

    /**
     * The baseUrl is as the same as Location
     *
     * A baseUrl has the same semantic with Jsoup.parse:
     * @link {https://jsoup.org/apidocs/org/jsoup/Jsoup.html#parse-java.io.File-java.lang.String-java.lang.String-}
     * @see WebPage#getLocation
     * */
    public String getBaseUrl() {
        return page.getBaseUrl() == null ? "" : page.getBaseUrl().toString();
    }

    /**
     * WebPage.url is the permanent internal address, it might not still available to access the target.
     * And WebPage.location or WebPage.baseUrl is the last working address, it might redirect to url,
     * or it might have additional random parameters.
     * WebPage.location may be different from url, it's generally normalized.
     */
    public String getLocation() {
        return getBaseUrl();
    }

    /**
     * The url is the permanent internal address, it might not still available to access the target.
     *
     * Location is the last working address, it might redirect to url, or it might have additional random parameters.
     *
     * Location may be different from url, it's generally normalized.
     */
    public void setLocation(@NotNull String value) {
        page.setBaseUrl(value);
    }

    @NotNull
    public Instant getFetchTime() {
        return Instant.ofEpochMilli(page.getFetchTime());
    }

    public void setFetchTime(@NotNull Instant time) {
        page.setFetchTime(time.toEpochMilli());
    }

    @NotNull
    public Instant getPrevFetchTime() {
        return Instant.ofEpochMilli(page.getPrevFetchTime());
    }

    public void setPrevFetchTime(@NotNull Instant time) {
        page.setPrevFetchTime(time.toEpochMilli());
    }

    /**
     * Get last fetch time
     * <p>
     * If fetchTime is before now, the result is the fetchTime
     * If fetchTime is after now, it means that schedule has modified it for the next fetch, the result is prevFetchTime
     */
    @NotNull
    public Instant getLastFetchTime(@NotNull Instant now) {
        Instant lastFetchTime = getFetchTime();
        if (lastFetchTime.isAfter(now)) {
            // fetch time is in the further, updated by schedule
            lastFetchTime = getPrevFetchTime();
        }
        return lastFetchTime;
    }

    @NotNull
    public Duration getFetchInterval() {
        return Duration.ofSeconds(page.getFetchInterval());
    }

    public void setFetchInterval(@NotNull Duration interval) {
        page.setFetchInterval((int) interval.getSeconds());
    }

    public long getFetchInterval(@NotNull TimeUnit destUnit) {
        return destUnit.convert(page.getFetchInterval(), TimeUnit.SECONDS);
    }

    public void setFetchInterval(long interval) {
        page.setFetchInterval((int) interval);
    }

    public void setFetchInterval(float interval) {
        page.setFetchInterval(Math.round(interval));
    }

    @NotNull
    public ProtocolStatus getProtocolStatus() {
        GProtocolStatus protocolStatus = page.getProtocolStatus();
        if (protocolStatus == null) {
            protocolStatus = GProtocolStatus.newBuilder().build();
        }
        return ProtocolStatus.box(protocolStatus);
    }

    public void setProtocolStatus(@NotNull ProtocolStatus protocolStatus) {
        page.setProtocolStatus(protocolStatus.unbox());
    }

    /**
     * Header information returned from the web server used to server the content which is subsequently fetched from.
     * This includes keys such as
     * TRANSFER_ENCODING,
     * CONTENT_ENCODING,
     * CONTENT_LANGUAGE,
     * CONTENT_LENGTH,
     * CONTENT_LOCATION,
     * CONTENT_DISPOSITION,
     * CONTENT_MD5,
     * CONTENT_TYPE,
     * LAST_MODIFIED
     * and LOCATION.
     */
    @NotNull
    public ProtocolHeaders getHeaders() {
        return ProtocolHeaders.box(page.getHeaders());
    }

    @NotNull
    public String getReprUrl() {
        return page.getReprUrl() == null ? "" : page.getReprUrl().toString();
    }

    public void setReprUrl(@NotNull String value) {
        page.setReprUrl(value);
    }

    /**
     * Get the number of crawl scope retries
     * @see ai.platon.pulsar.persist.RetryScope
     * */
    public int getFetchRetries() {
        return page.getFetchRetries();
    }

    /**
     * Set the number of crawl scope retries
     * @see ai.platon.pulsar.persist.RetryScope
     * */
    public void setFetchRetries(int value) {
        page.setFetchRetries(value);
    }

    @NotNull
    public Duration getLastTimeout() {
        String s = getMetadata().get(Name.RESPONSE_TIME);
        return s == null ? Duration.ZERO : Duration.parse(s);
    }

    @NotNull
    public Instant getModifiedTime() {
        return Instant.ofEpochMilli(page.getModifiedTime());
    }

    public void setModifiedTime(@NotNull Instant value) {
        page.setModifiedTime(value.toEpochMilli());
    }

    @NotNull
    public Instant getPrevModifiedTime() {
        return Instant.ofEpochMilli(page.getPrevModifiedTime());
    }

    public void setPrevModifiedTime(@NotNull Instant value) {
        page.setPrevModifiedTime(value.toEpochMilli());
    }

    @NotNull
    public Instant sniffModifiedTime() {
        Instant modifiedTime = getModifiedTime();
        Instant headerModifiedTime = getHeaders().getLastModified();
        Instant contentModifiedTime = getContentModifiedTime();

        if (isValidContentModifyTime(headerModifiedTime) && headerModifiedTime.isAfter(modifiedTime)) {
            modifiedTime = headerModifiedTime;
        }

        if (isValidContentModifyTime(contentModifiedTime) && contentModifiedTime.isAfter(modifiedTime)) {
            modifiedTime = contentModifiedTime;
        }

        Instant contentPublishTime = getContentPublishTime();
        if (isValidContentModifyTime(contentPublishTime) && contentPublishTime.isAfter(modifiedTime)) {
            modifiedTime = contentPublishTime;
        }

        // A fix
        if (modifiedTime.isAfter(Instant.now().plus(1, ChronoUnit.DAYS))) {
            // LOG.warn("Invalid modified time " + DateTimeUtil.isoInstantFormat(modifiedTime) + ", url : " + page.url());
            modifiedTime = Instant.now();
        }

        return modifiedTime;
    }

    @NotNull
    public String getFetchTimeHistory(@NotNull String defaultValue) {
        String s = getMetadata().get(Name.FETCH_TIME_HISTORY);
        return s == null ? defaultValue : s;
    }

    /********************************************************************************
     * Parsing
     ********************************************************************************/

    public void putFetchTimeHistory(@NotNull Instant fetchTime) {
        String fetchTimeHistory = getMetadata().get(Name.FETCH_TIME_HISTORY);
        fetchTimeHistory = DateTimes.constructTimeHistory(fetchTimeHistory, fetchTime, 10);
        getMetadata().set(Name.FETCH_TIME_HISTORY, fetchTimeHistory);
    }

    public @NotNull Instant getFirstCrawlTime(@NotNull Instant defaultValue) {
        Instant firstCrawlTime = null;

        String fetchTimeHistory = getFetchTimeHistory("");
        if (!fetchTimeHistory.isEmpty()) {
            String[] times = fetchTimeHistory.split(",");
            Instant time = DateTimes.parseInstant(times[0], Instant.EPOCH);
            if (time.isAfter(Instant.EPOCH)) {
                firstCrawlTime = time;
            }
        }

        return firstCrawlTime == null ? defaultValue : firstCrawlTime;
    }

    /**
     * namespace : metadata, seed, www
     * reserved
     */
    @Nullable
    public String getNamespace() {
        return getMetadata().get("namespace");
    }

    /**
     * reserved
     * */
    public void setNamespace(String ns) {
        getMetadata().set("namespace", ns);
    }

    @NotNull
    public PageCategory getPageCategory() {
        try {
            if (page.getPageCategory() != null) {
                return PageCategory.valueOf(page.getPageCategory().toString());
            }
        } catch (Throwable ignored) {
        }

        return PageCategory.UNKNOWN;
    }

    /**
     * category : index, detail, review, media, search, etc
     */
    public void setPageCategory(@NotNull PageCategory pageCategory) {
        page.setPageCategory(pageCategory.name());
    }

    @Nullable
    public String getEncoding() {
        return page.getEncoding() == null ? null : page.getEncoding().toString();
    }

    public void setEncoding(@Nullable String encoding) {
        page.setEncoding(encoding);
        getMetadata().set(Name.CHAR_ENCODING_FOR_CONVERSION, encoding);
    }

    /**
     * Get content encoding
     * Content encoding is detected just before it's parsed
     */
    @NotNull
    public String getEncodingOrDefault(@NotNull String defaultEncoding) {
        return page.getEncoding() == null ? defaultEncoding : page.getEncoding().toString();
    }

    @NotNull
    public String getEncodingClues() {
        return getMetadata().getOrDefault(Name.ENCODING_CLUES, "");
    }

    public void setEncodingClues(@NotNull String clues) {
        getMetadata().set(Name.ENCODING_CLUES, clues);
    }

    public boolean hasContent() {
        return page.getContent() != null;
    }

    /**
     * The entire raw document content e.g. raw XHTML
     */
    @Nullable
    public ByteBuffer getContent() {
        if (cachedContent != null) {
            return cachedContent;
        }
        return page.getContent();
    }

    @Nullable
    public ByteBuffer getCachedContent() {
        return cachedContent;
    }

    public void setCachedContent(ByteBuffer cachedContent) {
        this.cachedContent = cachedContent;
    }

    @Nullable
    public ByteBuffer getUncachedContent() {
        return page.getContent();
    }

    @NotNull
    public byte[] getContentAsBytes() {
        ByteBuffer content = getContent();
        if (content == null) {
            return ByteUtils.toBytes('\0');
        }
        return Bytes.getBytes(content);
    }

    /**
     * TODO: Encoding is always UTF-8?
     */
    @NotNull
    public String getContentAsString() {
        return Bytes.toString(getContentAsBytes());
    }

    @NotNull
    public ByteArrayInputStream getContentAsInputStream() {
        ByteBuffer contentInOctets = getContent();
        if (contentInOctets == null) {
            return new ByteArrayInputStream(ByteUtils.toBytes('\0'));
        }

        return new ByteArrayInputStream(getContent().array(),
                contentInOctets.arrayOffset() + contentInOctets.position(),
                contentInOctets.remaining());
    }

    @NotNull
    public InputSource getContentAsSaxInputSource() {
        InputSource inputSource = new InputSource(getContentAsInputStream());
        String encoding = getEncoding();
        if (encoding != null) {
            inputSource.setEncoding(encoding);
        }
        return inputSource;
    }

    public void setContent(@Nullable String value) {
        if (value != null) {
            setContent(value.getBytes());
        } else {
            setContent((ByteBuffer)null);
        }
    }

    public void setContent(@Nullable byte[] value) {
        if (value != null) {
            setContent(ByteBuffer.wrap(value));
        } else {
            setContent((ByteBuffer)null);
        }
    }

    public void setContent(@Nullable ByteBuffer value) {
        if (value != null) {
            page.setContent(value);
            setContentBytes(value.array().length);
        } else {
            page.setContent(null);
            setContentBytes(0);
        }
    }

    /**
     * TODO: check consistency with HttpHeaders.CONTENT_LENGTH
     * */
    public int getContentBytes() {
        return getMetadata().getInt(Name.CONTENT_BYTES, 0);
    }

    private void setContentBytes(int bytes) {
        if (bytes == 0) {
            return;
        }

        getMetadata().set(Name.CONTENT_BYTES, String.valueOf(bytes));

        int count = getFetchCount();
        int lastAveBytes = getMetadata().getInt(Name.AVE_CONTENT_BYTES, 0);

        int aveBytes;
        if (count > 0 && lastAveBytes == 0) {
            // old version, average bytes is not calculated
            aveBytes = bytes;
        } else {
            aveBytes = (lastAveBytes * count + bytes) / (count + 1);
        }

        getMetadata().set(Name.AVE_CONTENT_BYTES, String.valueOf(aveBytes));
    }

    public int getAveContentBytes() {
        return getMetadata().getInt(Name.AVE_CONTENT_BYTES, 0);
    }

    @NotNull
    public String getContentType() {
        return page.getContentType() == null ? "" : page.getContentType().toString();
    }

    public void setContentType(String value) {
        page.setContentType(value.trim().toLowerCase());
    }

    @Nullable
    public ByteBuffer getPrevSignature() {
        return page.getPrevSignature();
    }

    public void setPrevSignature(@Nullable ByteBuffer value) {
        page.setPrevSignature(value);
    }

    @NotNull
    public String getPrevSignatureAsString() {
        ByteBuffer sig = getPrevSignature();
        if (sig == null) {
            sig = ByteBuffer.wrap("".getBytes());
        }
        return Strings.toHexString(sig);
    }

    @Nullable
    public String getProxy() {
        return getMetadata().get(Name.PROXY);
    }

    public void setProxy(@Nullable String proxy) {
        if (proxy != null) {
            getMetadata().set(Name.PROXY, proxy);
        }
    }

    // TODO: use a separate avro field to hold BROWSER_JS_DATA
    @Nullable
    public ActiveDomMultiStatus getActiveDomMultiStatus() {
        // cached
        Name name = Name.ACTIVE_DOM_MULTI_STATUS;
        Object value = variables.get(name);
        if (value instanceof ActiveDomMultiStatus) {
            return (ActiveDomMultiStatus)value;
        } else {
            String json = getMetadata().get(name);
            if (json != null) {
                ActiveDomMultiStatus status = ActiveDomMultiStatus.Companion.fromJson(json);
                variables.set(name, status);
                return status;
            }
        }

        return null;
    }

    public void setActiveDomMultiStatus(@Nullable ActiveDomMultiStatus domStatus) {
        if (domStatus != null) {
            variables.set(Name.ACTIVE_DOM_MULTI_STATUS, domStatus);
            getMetadata().set(Name.ACTIVE_DOM_MULTI_STATUS, domStatus.toJson());
        }
    }

    @Nullable
    public ActiveDomUrls getActiveDomUrls() {
        // cached
        Name name = Name.ACTIVE_DOM_URLS;
        Object value = variables.get(name);
        if (value instanceof ActiveDomUrls) {
            return (ActiveDomUrls)value;
        } else {
            String json = getMetadata().get(name);
            if (json != null) {
                ActiveDomUrls status = ActiveDomUrls.Companion.fromJson(json);
                variables.set(name, status);
                return status;
            }
        }

        return null;
    }

    public void setActiveDomUrls(@Nullable ActiveDomUrls urls) {
        if (urls != null) {
            variables.set(Name.ACTIVE_DOM_URLS, urls);
            getMetadata().set(Name.ACTIVE_DOM_URLS, urls.toJson());
        }
    }

    /**
     * An implementation of a WebPage's signature from which it can be identified and referenced at any point in time.
     * This is essentially the WebPage's fingerprint representing its state for any point in time.
     */
    @Nullable
    public ByteBuffer getSignature() {
        return page.getSignature();
    }

    public void setSignature(byte[] value) {
        page.setSignature(ByteBuffer.wrap(value));
    }

    @NotNull
    public String getSignatureAsString() {
        ByteBuffer sig = getSignature();
        if (sig == null) {
            sig = ByteBuffer.wrap("".getBytes());
        }
        return Strings.toHexString(sig);
    }

    @NotNull
    public String getPageTitle() {
        return page.getPageTitle() == null ? "" : page.getPageTitle().toString();
    }

    public void setPageTitle(String pageTitle) {
        page.setPageTitle(pageTitle);
    }

    @NotNull
    public String getContentTitle() {
        return page.getContentTitle() == null ? "" : page.getContentTitle().toString();
    }

    public void setContentTitle(String contentTitle) {
        if (contentTitle != null) {
            page.setContentTitle(contentTitle);
        }
    }

    @NotNull
    public String sniffTitle() {
        String title = getContentTitle();
        if (title.isEmpty()) {
            title = getAnchor().toString();
        }
        if (title.isEmpty()) {
            title = getPageTitle();
        }
        if (title.isEmpty()) {
            title = getLocation();
        }
        if (title.isEmpty()) {
            title = url;
        }
        return title;
    }

    @NotNull
    public String getPageText() {
        return page.getPageText() == null ? "" : page.getPageText().toString();
    }

    public void setPageText(String value) {
        if (value != null && !value.isEmpty()) page.setPageText(value);
    }

    @NotNull
    public String getContentText() {
        return page.getContentText() == null ? "" : page.getContentText().toString();
    }

    public void setContentText(String textContent) {
        if (textContent != null && !textContent.isEmpty()) {
            page.setContentText(textContent);
            page.setContentTextLen(textContent.length());
        }
    }

    public int getContentTextLen() {
        return page.getContentTextLen();
    }

    /**
     * Set all text fields cascaded, including content, content text and page text.
     */
    public void setTextCascaded(String text) {
        setContent(text);
        setContentText(text);
        setPageText(text);
    }

    /**
     * {WebPage#setParseStatus} must be called later if the status is empty
     */
    @NotNull
    public ParseStatus getParseStatus() {
        GParseStatus parseStatus = page.getParseStatus();
        return ParseStatus.box(parseStatus == null ? GParseStatus.newBuilder().build() : parseStatus);
    }

    public void setParseStatus(ParseStatus parseStatus) {
        page.setParseStatus(parseStatus.unbox());
    }

    /**
     * Embedded hyperlinks which direct outside of the current domain.
     */
    public Map<CharSequence, GHypeLink> getLiveLinks() {
        return page.getLiveLinks();
    }

    public Collection<String> getSimpleLiveLinks() {
        return CollectionUtils.collect(page.getLiveLinks().keySet(), CharSequence::toString);
    }

    /**
     * TODO: Remove redundant url to reduce space
     */
    public void setLiveLinks(Iterable<HyperLink> liveLinks) {
        page.getLiveLinks().clear();
        Map<CharSequence, GHypeLink> links = page.getLiveLinks();
        liveLinks.forEach(l -> links.put(l.getUrl(), l.unbox()));
    }

    /**
     * @param links the value to set.
     */
    public void setLiveLinks(Map<CharSequence, GHypeLink> links) {
        page.setLiveLinks(links);
    }

    public void addLiveLink(HyperLink hyperLink) {
        page.getLiveLinks().put(hyperLink.getUrl(), hyperLink.unbox());
    }

    public Map<CharSequence, CharSequence> getVividLinks() {
        return page.getVividLinks();
    }

    public Collection<String> getSimpleVividLinks() {
        return CollectionUtils.collect(page.getVividLinks().keySet(), CharSequence::toString);
    }

    public void setVividLinks(Map<CharSequence, CharSequence> links) {
        page.setVividLinks(links);
    }

    public List<CharSequence> getDeadLinks() {
        return page.getDeadLinks();
    }

    public void setDeadLinks(List<CharSequence> deadLinks) {
        page.setDeadLinks(deadLinks);
    }

    public List<CharSequence> getLinks() {
        return page.getLinks();
    }

    public void setLinks(List<CharSequence> links) {
        page.setLinks(links);
    }

    /**
     * Record all links appeared in a page
     * The links are in FIFO order, for each time we fetch and parse a page,
     * we push newly discovered links to the queue, if the queue is full, we drop out some old ones,
     * usually they do not appears in the page any more.
     * <p>
     * TODO: compress links
     * TODO: HBase seems not modify any nested array
     */
    public void addHyperLinks(Iterable<HyperLink> hypeLinks) {
        List<CharSequence> links = page.getLinks();

        // If there are too many links, Drop the front 1/3 links
        if (links.size() > MAX_LINK_PER_PAGE) {
            links = links.subList(links.size() - MAX_LINK_PER_PAGE / 3, links.size());
        }

        for (HyperLink l : hypeLinks) {
            Utf8 url = u8(l.getUrl());
            if (!links.contains(url)) {
                links.add(url);
            }
        }

        setLinks(links);
        setImpreciseLinkCount(links.size());
    }

    public void addLinks(Iterable<CharSequence> hypeLinks) {
        List<CharSequence> links = page.getLinks();

        // If there are too many links, Drop the front 1/3 links
        if (links.size() > MAX_LINK_PER_PAGE) {
            links = links.subList(links.size() - MAX_LINK_PER_PAGE / 3, links.size());
        }

        for (CharSequence link : hypeLinks) {
            Utf8 url = u8(link.toString());
            // Use a set?
            if (!links.contains(url)) {
                links.add(url);
            }
        }

        setLinks(links);
        setImpreciseLinkCount(links.size());
    }

    public int getImpreciseLinkCount() {
        String count = getMetadata().getOrDefault(Name.TOTAL_OUT_LINKS, "0");
        return NumberUtils.toInt(count, 0);
    }

    public void setImpreciseLinkCount(int count) {
        getMetadata().set(Name.TOTAL_OUT_LINKS, String.valueOf(count));
    }

    public void increaseImpreciseLinkCount(int count) {
        int oldCount = getImpreciseLinkCount();
        setImpreciseLinkCount(oldCount + count);
    }

    public Map<CharSequence, CharSequence> getInlinks() {
        return page.getInlinks();
    }

    @NotNull
    public CharSequence getAnchor() {
        return page.getAnchor() != null ? page.getAnchor() : "";
    }

    /**
     * Anchor can be used to sniff article title
     */
    public void setAnchor(CharSequence anchor) {
        page.setAnchor(anchor);
    }

    public String[] getInlinkAnchors() {
        return StringUtils.split(getMetadata().getOrDefault(Name.ANCHORS, ""), "\n");
    }

    public void setInlinkAnchors(Collection<CharSequence> anchors) {
        getMetadata().set(Name.ANCHORS, StringUtils.join(anchors, "\n"));
    }

    public int getAnchorOrder() {
        int order = page.getAnchorOrder();
        return order < 0 ? MAX_LIVE_LINK_PER_PAGE : order;
    }

    public void setAnchorOrder(int order) {
        page.setAnchorOrder(order);
    }

    public Instant getContentPublishTime() {
        return Instant.ofEpochMilli(page.getContentPublishTime());
    }

    public void setContentPublishTime(Instant publishTime) {
        page.setContentPublishTime(publishTime.toEpochMilli());
    }

    private boolean isValidContentModifyTime(Instant publishTime) {
        return publishTime.isAfter(MIN_ARTICLE_PUBLISH_TIME) && publishTime.isBefore(AppContext.INSTANCE.getImprecise2DaysAhead());
    }

    public boolean updateContentPublishTime(Instant newPublishTime) {
        if (!isValidContentModifyTime(newPublishTime)) {
            return false;
        }

        Instant lastPublishTime = getContentPublishTime();
        if (newPublishTime.isAfter(lastPublishTime)) {
            setPrevContentPublishTime(lastPublishTime);
            setContentPublishTime(newPublishTime);
        }

        return true;
    }

    public Instant getPrevContentPublishTime() {
        return Instant.ofEpochMilli(page.getPrevContentPublishTime());
    }

    public void setPrevContentPublishTime(Instant publishTime) {
        page.setPrevContentPublishTime(publishTime.toEpochMilli());
    }

    public Instant getRefContentPublishTime() {
        return Instant.ofEpochMilli(page.getRefContentPublishTime());
    }

    public void setRefContentPublishTime(Instant publishTime) {
        page.setRefContentPublishTime(publishTime.toEpochMilli());
    }

    public Instant getContentModifiedTime() {
        return Instant.ofEpochMilli(page.getContentModifiedTime());
    }

    public void setContentModifiedTime(Instant modifiedTime) {
        page.setContentModifiedTime(modifiedTime.toEpochMilli());
    }

    public Instant getPrevContentModifiedTime() {
        return Instant.ofEpochMilli(page.getPrevContentModifiedTime());
    }

    public void setPrevContentModifiedTime(Instant modifiedTime) {
        page.setPrevContentModifiedTime(modifiedTime.toEpochMilli());
    }

    public boolean updateContentModifiedTime(Instant newModifiedTime) {
        if (!isValidContentModifyTime(newModifiedTime)) {
            return false;
        }

        Instant lastModifyTime = getContentModifiedTime();
        if (newModifiedTime.isAfter(lastModifyTime)) {
            setPrevContentModifiedTime(lastModifyTime);
            setContentModifiedTime(newModifiedTime);
        }

        return true;
    }

    public Instant getPrevRefContentPublishTime() {
        return Instant.ofEpochMilli(page.getPrevRefContentPublishTime());
    }

    public void setPrevRefContentPublishTime(Instant publishTime) {
        page.setPrevRefContentPublishTime(publishTime.toEpochMilli());
    }

    public boolean updateRefContentPublishTime(Instant newRefPublishTime) {
        if (!isValidContentModifyTime(newRefPublishTime)) {
            return false;
        }

        Instant latestRefPublishTime = getRefContentPublishTime();

        // LOG.debug("Ref Content Publish Time: " + latestRefPublishTime + " -> " + newRefPublishTime + ", Url: " + getUrl());

        if (newRefPublishTime.isAfter(latestRefPublishTime)) {
            setPrevRefContentPublishTime(latestRefPublishTime);
            setRefContentPublishTime(newRefPublishTime);

            // LOG.debug("[Updated] " + latestRefPublishTime + " -> " + newRefPublishTime);

            return true;
        }

        return false;
    }

    @NotNull
    public String getReferrer() {
        return page.getReferrer() == null ? "" : page.getReferrer().toString();
    }

    public void setReferrer(String referrer) {
        if (referrer != null && referrer.length() > SHORTEST_VALID_URL_LENGTH) {
            page.setReferrer(referrer);
        }
    }

    /********************************************************************************
     * Page Model
     ********************************************************************************/

    @NotNull
    public PageModel getPageModel() {
        return PageModel.box(page.getPageModel());
    }

    /********************************************************************************
     * Scoring
     ********************************************************************************/

    public float getScore() {
        return page.getScore();
    }

    public void setScore(float value) {
        page.setScore(value);
    }

    public float getContentScore() {
        return page.getContentScore() == null ? 0.0f : page.getContentScore();
    }

    public void setContentScore(float score) {
        page.setContentScore(score);
    }

    @NotNull
    public String getSortScore() {
        return page.getSortScore() == null ? "" : page.getSortScore().toString();
    }

    public void setSortScore(String score) {
        page.setSortScore(score);
    }

    public float getCash() {
        return getMetadata().getFloat(Name.CASH_KEY, 0.0f);
    }

    public void setCash(float cash) {
        getMetadata().set(Name.CASH_KEY, String.valueOf(cash));
    }

    @NotNull
    public PageCounters getPageCounters() {
        return PageCounters.box(page.getPageCounters());
    }

    /********************************************************************************
     * Index
     ********************************************************************************/

    public String getIndexTimeHistory(String defaultValue) {
        String s = getMetadata().get(Name.INDEX_TIME_HISTORY);
        return s == null ? defaultValue : s;
    }

    public void putIndexTimeHistory(Instant indexTime) {
        String indexTimeHistory = getMetadata().get(Name.INDEX_TIME_HISTORY);
        indexTimeHistory = DateTimes.constructTimeHistory(indexTimeHistory, indexTime, 10);
        getMetadata().set(Name.INDEX_TIME_HISTORY, indexTimeHistory);
    }

    public Instant getFirstIndexTime(Instant defaultValue) {
        Instant firstIndexTime = null;

        String indexTimeHistory = getIndexTimeHistory("");
        if (!indexTimeHistory.isEmpty()) {
            String[] times = indexTimeHistory.split(",");
            Instant time = DateTimes.parseInstant(times[0], Instant.EPOCH);
            if (time.isAfter(Instant.EPOCH)) {
                firstIndexTime = time;
            }
        }

        return firstIndexTime == null ? defaultValue : firstIndexTime;
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public int compareTo(@NotNull WebPage o) {
        return url.compareTo(o.url);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WebPage && ((WebPage) other).url.equals(url);
    }

    @Override
    public String toString() {
        return new WebPageFormatter(this).format();
    }
}
