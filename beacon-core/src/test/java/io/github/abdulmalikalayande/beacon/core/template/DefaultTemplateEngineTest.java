package io.github.abdulmalikalayande.beacon.core.template;

import io.github.abdulmalikalayande.beacon.api.dto.NotificationPreference;
import io.github.abdulmalikalayande.beacon.api.dto.NotificationTemplate;
import io.github.abdulmalikalayande.beacon.api.dto.RenderedNotification;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel;
import io.github.abdulmalikalayande.beacon.api.enums.NotificationType;
import io.github.abdulmalikalayande.beacon.api.port.TemplateEngine;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import static io.github.abdulmalikalayande.beacon.api.enums.NotificationChannel.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test suite for {@link DefaultTemplateEngine}, the single-pass regex-based
 * implementation of the {@link TemplateEngine} port defined in {@code beacon-api}.
 *
 * <p>This suite verifies:
 * <ul>
 *     <li><b>Placeholder substitution</b> — correct replacement of {@code ${key}} markers
 *         in subject, title, and body template strings using values from the context map.</li>
 *     <li><b>Destination routing</b> — correct extraction of the recipient address (email,
 *         phone number, or push token) based on the notification channel.</li>
 *     <li><b>Edge-case safety</b> — null templates, empty templates, null context maps,
 *         missing keys, null values, and regex-special characters in context values.</li>
 *     <li><b>Contract pinning</b> — unresolved placeholders (missing or null-valued keys)
 *         are left as literal {@code ${key}} text in the output rather than silently dropped
 *         or causing exceptions.</li>
 * </ul>
 *
 * <p>All tests are pure unit tests with no Spring context, no database, and no I/O.
 * The engine is instantiated directly as a plain object.
 *
 * @see DefaultTemplateEngine
 * @see TemplateEngine
 */
public class DefaultTemplateEngineTest {
	
	private final TemplateEngine templateEngine = new DefaultTemplateEngine();
	
	/**
	 * Verifies the primary happy-path contract: all {@code ${key}} placeholders in the
	 * subject and body are replaced with their corresponding context values, and the
	 * destination is correctly routed for the EMAIL channel.
	 *
	 * <p>Uses a fully-populated {@link NotificationPreference} (email, phone, push token,
	 * enabled channels, timezone, quiet hours) to verify that the engine does not depend on
	 * or interfere with preference fields beyond the destination address.
	 *
	 * <p>Assertions cover:
	 * <ul>
	 *     <li>Subject: {@code ${name}} replaced with "Alice".</li>
	 *     <li>Title: null input produces null output (email has no title).</li>
	 *     <li>Body: both {@code ${name}} and {@code ${orderId}} replaced.</li>
	 *     <li>Destination: EMAIL channel routes to the preference's email address.</li>
	 *     <li>Channel: the output's channel matches the template's channel.</li>
	 * </ul>
	 */
	@Test
	public void render_happyPath_replacesAllPlaceholders() {
		NotificationTemplate templateObj = new NotificationTemplate(
				NotificationType.ORDER_CONFIRMATION,
				EMAIL,
				"Order Confirmation for ${name}",
				null,
				"Hello ${name}, your order ${orderId} is confirmed!"
		);
		NotificationPreference preference = new NotificationPreference(
				UUID.randomUUID().toString(),
				"aliceekubo@gmail.com",
				"+2349143768922",
				null,
				Set.of(EMAIL, SMS, PUSH),
				TimeZone.getDefault().getID(),
				LocalTime.MIDNIGHT,
				LocalTime.of(7, 0, 0)
		);
		Map<String, String> context = Map.of("name", "Alice", "orderId", "12345");

		RenderedNotification result = templateEngine.render(templateObj, preference, context);

		assertEquals("Order Confirmation for Alice", result.subject());
		assertNull(result.title());
		assertEquals("Hello Alice, your order 12345 is confirmed!", result.body());
		assertEquals("aliceekubo@gmail.com", result.to());
		assertEquals(EMAIL, result.channel());
	}
	
	/**
	 * Verifies that subject, title, and body are each rendered independently from their
	 * own template string, not from a single shared rendering pass.
	 *
	 * <p>Uses different placeholder keys across fields — {@code ${username}} in subject,
	 * {@code ${username}} + {@code ${device}} in title, {@code ${device}} + {@code ${location}}
	 * in body. If the engine incorrectly shared state between fields, substitutions would
	 * bleed across boundaries. The PUSH channel is used because it is the only channel where
	 * both title and body are semantically relevant.
	 *
	 * <p>Assertions cover all five output fields: subject, title, body, destination (push
	 * token), and channel.
	 */
	@Test
	public void render_multiFieldTemplates_replaces_Subject_Title_And_Body() {
		NotificationTemplate template = createTemplate(
				PUSH,
				"Security alert for ${username}",
				"${username}, new sign-in on ${device}",
				"A sign-in from ${device} was detected at ${location}. If this was not you, act immediately."
		);
		NotificationPreference preference = createPreference(null, null, "fcm-token-xyz");
		Map<String, String> context = Map.of(
				"username", "Abdulmalik",
				"device",   "MacBook Pro",
				"location", "Lagos, NG"
		);

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals("Security alert for Abdulmalik", result.subject());
		assertEquals("Abdulmalik, new sign-in on MacBook Pro", result.title());
		assertEquals("A sign-in from MacBook Pro was detected at Lagos, NG. If this was not you, act immediately.", result.body());
		assertEquals("fcm-token-xyz", result.to());
		assertEquals(PUSH, result.channel());
	}
	
	/**
	 * Verifies that the engine's internal {@code renderString} method short-circuits safely
	 * when all three template fields (subject, title, body) are {@code null}.
	 *
	 * <p>The implementation guards with {@code if (templateString == null || templateString.isBlank())}
	 * and returns the input unchanged. This test pins the null branch of that guard: null input
	 * must produce null output without throwing {@link NullPointerException}. It also verifies
	 * that destination routing is unaffected — the EMAIL channel still resolves to the
	 * preference's email address even when all template content is null.
	 *
	 * <p>A context map with values is deliberately provided to prove that the null guard
	 * activates before any placeholder scanning occurs.
	 */
	@Test
	public void render_nullTemplates_safeAndNoException() {
		NotificationTemplate template = createTemplate(EMAIL, null, null, null);
		NotificationPreference preference = createPreference(null, "user@example.com", null);
		Map<String, String> context = Map.of("name", "Test");

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertNull(result.subject());
		assertNull(result.title());
		assertNull(result.body());
		assertEquals("user@example.com", result.to());
	}
	
	/**
	 * Verifies that the engine's internal {@code renderString} method short-circuits on
	 * empty strings and returns them unchanged — as empty strings, not as {@code null}.
	 *
	 * <p>{@code "".isBlank()} returns {@code true} in Java, so the early-return guard
	 * activates and returns the original {@code ""} value. This test distinguishes the
	 * empty-string identity from the null identity tested in
	 * {@link #render_nullTemplates_safeAndNoException()}: downstream providers may treat
	 * {@code null} and {@code ""} differently, so the engine must preserve whichever the
	 * template author supplied.
	 *
	 * <p>A populated context map is provided to prove the early-return fires before any
	 * placeholder matching begins.
	 */
	@Test
	public void render_emptyTemplates_returnsEmptyStrings() {
		NotificationTemplate template = createTemplate(EMAIL, "", "", "");
		NotificationPreference preference = createPreference(null, "user@example.com", null);
		Map<String, String> context = Map.of("name", "Test");

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals("", result.subject());
		assertEquals("", result.title());
		assertEquals("", result.body());
		assertEquals("user@example.com", result.to());
	}
	
	/**
	 * Pins the engine's contract for unresolvable placeholders: when a {@code ${key}} has
	 * no usable value in the context — either because the key is entirely absent or because
	 * the key maps to {@code null} — the original literal placeholder text is left in the
	 * output unchanged.
	 *
	 * <p>This is a deliberate design decision for a notification infrastructure library.
	 * Silently dropping to an empty string hides misconfiguration (a user receives
	 * "Your code is " with no code and no one notices). Throwing an exception crashes the
	 * entire send for a missing optional field, which is worse than a slightly imperfect
	 * message. Leaving the literal {@code ${key}} is loud — it shows up immediately in
	 * testing and never silently corrupts a message.
	 *
	 * <p>This test verifies both sub-cases of "no usable value":
	 * <ul>
	 *     <li><b>Absent key:</b> {@code ${amount}} is not in the context map at all.</li>
	 *     <li><b>Null-valued key:</b> {@code ${code}} is present in the map but mapped to
	 *         {@code null}. Prior to the Bug 2 fix, this path crashed with
	 *         {@link NullPointerException} inside {@code Matcher.quoteReplacement(null)}.
	 *         After the fix, it is treated identically to an absent key.</li>
	 * </ul>
	 *
	 * <p>A {@link HashMap} is used instead of {@link Map#of()} because the latter does not
	 * permit {@code null} values.
	 */
	@Test
	public void render_missingKey_placeholderBehaviorDefined() {
		NotificationTemplate template = createTemplate(
				EMAIL, null, null,
				"Hello ${name}, your balance is ${amount}. Use code ${code} to redeem."
		);
		NotificationPreference preference = createPreference(null, "user@example.com", null);
		HashMap<String, String> context = new HashMap<>();
		context.put("name", "Fatima");
		context.put("code", null);

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals(
				"Hello Fatima, your balance is ${amount}. Use code ${code} to redeem.",
				result.body()
		);
	}
	
	/**
	 * Verifies the engine's behavior when a {@link NotificationTemplate} is constructed with
	 * a {@code null} channel.
	 *
	 * <p>The {@link NotificationChannel} enum is exhaustive — {@code PUSH}, {@code SMS}, and
	 * {@code EMAIL} cover every possible value — so there is no "unknown" channel at compile
	 * time. However, a {@code null} channel is possible if a template is constructed with
	 * {@code channel = null}. The engine's {@code extractDestination} method uses a
	 * {@code switch} expression on the channel enum, and switching on a {@code null} enum
	 * reference throws {@link NullPointerException} in Java.
	 *
	 * <p>This test verifies that the NPE propagates unhandled. A null channel is a programming
	 * error at the call site (the template was malformed), not an expected runtime condition.
	 * The engine must not mask it with a catch-all or a silent default — the caller must know
	 * they produced a bad template. Template rendering (subject, title, body) completes
	 * successfully before the channel switch is reached, but the overall {@code render()} call
	 * must still fail.
	 *
	 * <p>The preference is deliberately non-null because the null-preference guard in
	 * {@code extractDestination} would short-circuit to {@code null} before the switch,
	 * masking the null-channel defect. A non-null preference forces execution into the switch.
	 */
	@Test
	public void render_unknownChannel_throwsOrHandlesGracefully() {
		NotificationTemplate template = new NotificationTemplate(
				NotificationType.PAYMENT_RECEIPT, null, "Subject", "Title", "Body"
		);
		NotificationPreference preference = createPreference("+2348012345678", "user@example.com", "fcm-token");
		Map<String, String> context = Map.of();

		assertThrows(NullPointerException.class, () ->
				templateEngine.render(template, preference, context)
		);
	}
	
	/**
	 * Verifies that passing {@code null} as the context map does not throw an exception.
	 *
	 * <p>The engine guards with {@code (context != null) ? context.get(key) : null} inside
	 * the rendering loop. When context is {@code null}, every placeholder resolves to
	 * {@code null} and is treated as unresolvable — left as its literal {@code ${key}} text.
	 *
	 * <p>This scenario is realistic: a host may fire a notification with a template that
	 * requires no dynamic values (e.g., a static system alert) and pass {@code null} for
	 * the context rather than an empty map. The engine must not crash in that case.
	 *
	 * <p>Assertions verify that the placeholder survives verbatim and that the SMS
	 * destination is still correctly routed from the preference's phone number.
	 */
	@Test
	public void render_nullContextMap_handlesSafely() {
		NotificationTemplate template = createTemplate(
				SMS, null, null,
				"Your account ${accountId} has been locked."
		);
		NotificationPreference preference = createPreference("+2348012345678", null, null);

		RenderedNotification result = templateEngine.render(template, preference, null);

		assertEquals("Your account ${accountId} has been locked.", result.body());
		assertEquals("+2348012345678", result.to());
	}
	
	/**
	 * Verifies that passing an empty (but non-null) context map does not throw an exception
	 * and leaves all placeholders as their literal {@code ${key}} text.
	 *
	 * <p>This is distinct from the null-context test in
	 * {@link #render_nullContextMap_handlesSafely()}: here, {@code context.get(key)} returns
	 * {@code null} because the key is absent from the map, rather than being short-circuited
	 * by a null reference check on the map itself. Both paths must produce identical output —
	 * unresolved placeholders left intact — but they exercise different branches in the
	 * rendering logic.
	 *
	 * <p>The template uses two different placeholder keys to verify that every placeholder
	 * in the template is individually left unresolved, not just the first one.
	 */
	@Test
	public void render_emptyContextMap_handlesSafely() {
		NotificationTemplate template = createTemplate(
				SMS, null, null,
				"Hello ${name}, your code is ${code}."
		);
		NotificationPreference preference = createPreference("+2348012345678", null, null);

		RenderedNotification result = templateEngine.render(template, preference, Map.of());

		assertEquals("Hello ${name}, your code is ${code}.", result.body());
		assertEquals("+2348012345678", result.to());
	}
	
	/**
	 * Verifies that passing {@code null} as the {@link NotificationTemplate} object causes
	 * the engine to throw {@link NullPointerException} immediately.
	 *
	 * <p>The {@code render} method accesses {@code template.subjectTemplate()} as its very
	 * first operation. A null template is a programming error on the caller's side — the
	 * library should never silently produce a null template, and the engine must not add
	 * a defensive null guard that would mask the defect. The NPE propagates to the caller,
	 * making the error visible at the earliest possible point.
	 *
	 * <p>A valid preference and a populated context are provided to ensure the failure
	 * originates from the null template access, not from downstream null handling in
	 * other parameters.
	 */
	@Test
	public void render_nullTemplateObject_throwsOrHandlesGracefully() {
		NotificationPreference preference = createPreference(null, "user@example.com", null);
		Map<String, String> context = Map.of("name", "Test");

		assertThrows(NullPointerException.class, () ->
				templateEngine.render(null, preference, context)
		);
	}
	
	/**
	 * Verifies that the engine handles a {@code null} {@link NotificationPreference} gracefully
	 * by rendering all template fields normally and setting the destination ({@code to}) to
	 * {@code null}.
	 *
	 * <p>The {@code extractDestination} method has an explicit null guard:
	 * {@code if (preference == null) return null}. This is a valid runtime scenario — a
	 * delivery worker may attempt to render a template before the user's contact details are
	 * resolved, or the host's {@code UserPreferenceResolver} may return {@code null} for a
	 * deactivated user. The engine must not crash; it produces a rendered notification with
	 * a null destination, and the caller decides whether to discard it or escalate.
	 *
	 * <p>Assertions verify:
	 * <ul>
	 *     <li>Subject and body are rendered with correct placeholder substitution — proving
	 *         that template rendering is completely independent of the preference object.</li>
	 *     <li>Title is {@code null} because the template supplies no title — proving that
	 *         the null preference does not interfere with the null-template short-circuit.</li>
	 *     <li>Destination ({@code to}) is {@code null} — proving the null guard activates.</li>
	 *     <li>Channel is preserved — the template's channel passes through to the output
	 *         regardless of whether the preference is present.</li>
	 * </ul>
	 */
	@Test
	public void render_nullPreferenceObject_throwsOrHandlesGracefully() {
		NotificationTemplate template = createTemplate(EMAIL, "Welcome, ${name}!", null, "Hello ${name}, your account is ready.");
		Map<String, String> context = Map.of("name", "Kemi");

		RenderedNotification result = templateEngine.render(template, null, context);

		assertEquals("Welcome, Kemi!", result.subject());
		assertNull(result.title());
		assertEquals("Hello Kemi, your account is ready.", result.body());
		assertNull(result.to());
		assertEquals(EMAIL, result.channel());
	}
	
	/**
	 * Verifies that context values containing characters with special meaning in Java regex
	 * replacement strings are rendered literally, not interpreted as regex backreferences
	 * or escape sequences.
	 *
	 * <p>The engine uses {@link java.util.regex.Matcher#appendReplacement} internally, which
	 * treats {@code $} as a group-reference prefix and {@code \} as an escape character in
	 * the replacement string. Without {@link java.util.regex.Matcher#quoteReplacement}, a
	 * context value like {@code "$5.00"} would be interpreted as "insert capture group 5
	 * followed by .00", causing an {@link IndexOutOfBoundsException} or corrupted output.
	 *
	 * <p>This test uses three deliberately hostile values:
	 * <ul>
	 *     <li>{@code "$5.00"} — dollar sign triggers group-reference parsing.</li>
	 *     <li>{@code "C:\\Users\\file"} — backslashes trigger escape parsing.</li>
	 *     <li>{@code "$1 \\2 ${nested}"} — combination of dollar, backslash, and a string
	 *         that looks like a placeholder but is a context <i>value</i>, not a template
	 *         key, so it must not be recursively expanded.</li>
	 * </ul>
	 */
	@Test
	public void render_contextValueContainsRegexChars_escapedCorrectly() {
		NotificationTemplate template = createTemplate(EMAIL, null, null, "Amount: ${amount}, Path: ${path}, Ref: ${ref}");
		NotificationPreference preference = createPreference(null, "user@example.com", null);
		Map<String, String> context = Map.of(
				"amount", "$5.00",
				"path",   "C:\\Users\\file",
				"ref",    "$1 \\2 ${nested}"
		);

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals("Amount: $5.00, Path: C:\\Users\\file, Ref: $1 \\2 ${nested}", result.body());
	}
	
	/**
	 * Verifies that two or more placeholders placed directly adjacent to each other — with
	 * no delimiter, whitespace, or literal text between them — are each independently resolved.
	 *
	 * <p>This tests the regex engine's cursor management within
	 * {@link java.util.regex.Matcher#appendReplacement}. After processing the first match,
	 * the internal cursor advances to the character immediately after the closing {@code '}'}.
	 * The next {@code matcher.find()} must pick up the second {@code ${...}} starting at
	 * that exact position. If the cursor overshoots by even one character (e.g., due to an
	 * off-by-one in the replacement logic), the second placeholder would be partially consumed
	 * as literal text or missed entirely.
	 *
	 * <p>The template uses three adjacent placeholders {@code ${first}${middle}${last}} with
	 * zero separators between them. Assertions verify that all three are independently resolved
	 * and their replacement values are concatenated directly in the output with no extraneous
	 * characters introduced between them.
	 */
	@Test
	public void render_placeholderAdjacent_noDelimitersBetween() {
		NotificationTemplate template = createTemplate(SMS, null, null, "${first}${middle}${last}");
		NotificationPreference preference = createPreference("+2348012345678", null, null);
		Map<String, String> context = Map.of(
			"first",  "Abdul",
			"middle", "malik",
			"last",   "Alayande"
		);

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals("AbdulmalikAlayande", result.body());
	}
	
	/**
	 * Verifies that an unclosed placeholder — a {@code ${} sequence with no matching closing
	 * {@code '}'} before the end of the template string — is left as literal text in the
	 * output and does not cause the engine to hang, crash, or corrupt adjacent content.
	 *
	 * <p>The regex pattern {@code \$\{([^}]+)}} requires a closing {@code '}'} to complete
	 * a match. When the template ends with {@code ${support} and no closing brace follows,
	 * the regex fails to match at that position. The text is emitted unchanged by
	 * {@code appendTail}.
	 *
	 * <p>The template deliberately places a valid placeholder ({@code ${code}}) before the
	 * unclosed one to verify that the engine correctly resolves valid placeholders even in
	 * the presence of malformed syntax downstream — the unclosed placeholder must not poison
	 * or interfere with earlier matches. The key {@code support} IS present in the context
	 * map, proving that having a matching key in the context does not help when the
	 * placeholder syntax itself is broken.
	 */
	@Test
	public void render_unclosedPlaceholder_leftUntouched() {
		NotificationTemplate template = createTemplate(EMAIL, null, null, "Your code is ${code}. Contact ${support");
		NotificationPreference preference = createPreference(null, "user@example.com", null);
		Map<String, String> context = Map.of("code", "847391", "support", "help@beacon.io");

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals("Your code is 847391. Contact ${support", result.body());
	}
	
	/**
	 * Verifies that a "nested" placeholder — a {@code ${...}} sequence whose captured key
	 * itself contains a {@code ${} prefix — is treated as a single flat key by the regex
	 * engine, not recursively expanded.
	 *
	 * <p>Given the template string {@code "${msg_${lang}}"}, the greedy character class
	 * {@code [^}]+} in the regex {@code \$\{([^}]+)}} matches everything between the
	 * <i>first</i> {@code ${} and the <i>first</i> closing {@code '}'}: that is, the
	 * captured group 1 is {@code "msg_${lang"}, and the overall match consumes
	 * {@code "${msg_${lang}"}. The trailing {@code "}"} after {@code lang} is literal text
	 * emitted by {@code appendTail}.
	 *
	 * <p>This means:
	 * <ul>
	 *     <li>The engine performs a single context lookup for the key {@code "msg_${lang"}
	 *         (after trimming). That key is absent from the context, so the entire match
	 *         {@code "${msg_${lang}"} is left as-is.</li>
	 *     <li>The trailing literal {@code "}"} is appended unchanged.</li>
	 *     <li>The nested placeholder {@code ${lang}} is never independently matched or
	 *         resolved — it is consumed as part of the outer key.</li>
	 * </ul>
	 *
	 * <p>This test pins that single-pass, non-recursive behavior: the output is the
	 * original template string unchanged.
	 */
	@Test
	public void render_nestedPlaceholder_treatedAsLiteral() {
		NotificationTemplate template = createTemplate(EMAIL, null, null, "${msg_${lang}}");
		NotificationPreference preference = createPreference(null, "user@example.com", null);
		Map<String, String> context = Map.of("lang", "en", "msg_en", "Welcome");

		RenderedNotification result = templateEngine.render(template, preference, context);

		// The regex captures "msg_${lang" as the key (up to first '}').
		// That key is not in the context, so it stays as literal. Trailing "}" from appendTail.
		assertEquals("${msg_${lang}}", result.body());
	}
	
	/**
	 * Verifies that when the same placeholder key appears multiple times in a single
	 * template string, every occurrence is replaced — not just the first match.
	 *
	 * <p>The engine's rendering loop uses {@code while (matcher.find())}, which iterates
	 * over all matches in the input. This test pins that iteration contract by placing
	 * {@code ${name}} at the start and end of the body, and {@code ${code}} twice in the
	 * middle. If the loop incorrectly exited after the first match or if
	 * {@code appendReplacement/appendTail} mishandled cursor state, at least one occurrence
	 * would survive as a literal.
	 *
	 * <p>The assertion verifies the fully-resolved output with no remaining {@code ${...}}
	 * markers, confirming all four placeholder occurrences were replaced.
	 */
	@Test
	public void render_repeatedPlaceholder_replacedEveryOccurrence() {
		NotificationTemplate template = createTemplate(SMS, null, null, "Hi ${name}! Your OTP is ${code}. Do not share ${code} with anyone, ${name}.");
		NotificationPreference preference = createPreference("+2348012345678", null, null);
		Map<String, String> context = Map.of("name", "Emeka", "code", "847391");

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals(
				"Hi Emeka! Your OTP is 847391. Do not share 847391 with anyone, Emeka.",
				result.body()
		);
	}
	
	/**
	 * Verifies that whitespace inside a placeholder's braces is trimmed before the context
	 * lookup, so {@code ${ name }} resolves identically to {@code ${name}}.
	 *
	 * <p>Template authors frequently introduce accidental spaces inside placeholders for
	 * readability or by typo: {@code ${ name }}, {@code ${name }}, {@code ${ name}}.
	 * Without trimming, these would silently fail to resolve because the context map is
	 * keyed on {@code "name"} (no whitespace), and the captured group would be
	 * {@code " name "} — a silent mismatch that is extremely hard to debug.
	 *
	 * <p>After the {@code .trim()} implementation change on the captured regex group,
	 * all whitespace variants resolve correctly. This test verifies three whitespace
	 * patterns in a single template:
	 * <ul>
	 *     <li>{@code ${ name }} — spaces on both sides.</li>
	 *     <li>{@code ${code }} — trailing space only.</li>
	 *     <li>{@code ${ amount}} — leading space only.</li>
	 * </ul>
	 *
	 * <p>All three must resolve to their context values despite the whitespace in the
	 * template syntax.
	 */
	@Test
	public void render_whitespaceInPlaceholderName_handledAsKey() {
		NotificationTemplate template = createTemplate(EMAIL, null, null, "Hello ${ name }, your code is ${code } and balance is ${ amount}.");
		NotificationPreference preference = createPreference(null, "user@example.com", null);
		Map<String, String> context = Map.of(
			"name",   "Chidi",
			"code",   "993201",
			"amount", "₦15,000"
		);

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals("Hello Chidi, your code is 993201 and balance is ₦15,000.", result.body());
	}
	
	/**
	 * Verifies that placeholder keys containing dots ({@code .}) and hyphens ({@code -})
	 * are treated as flat string keys in the context map — not as path-navigation operators
	 * or expression delimiters.
	 *
	 * <p>In many template engines (Thymeleaf, FreeMarker, Handlebars), a dot in a variable
	 * name triggers property-path traversal: {@code ${user.name}} navigates to
	 * {@code user.getName()}. Beacon's {@code DefaultTemplateEngine} is a simple
	 * key-value replacer — the entire captured group (after trimming) is used as a single
	 * {@code Map.get()} key. There is no object graph, no path navigation, no expression
	 * evaluation.
	 *
	 * <p>This test uses three deliberately "structured-looking" keys:
	 * <ul>
	 *     <li>{@code "user.name"} — dot-separated, looks like a property path.</li>
	 *     <li>{@code "order-id"} — hyphenated, looks like a kebab-case identifier.</li>
	 *     <li>{@code "meta.tx-ref"} — combination of dot and hyphen.</li>
	 * </ul>
	 *
	 * <p>All three are present in the context map as flat string keys, and all three must
	 * resolve correctly. This pins the contract that the engine does not interpret key
	 * syntax — it performs an exact-match lookup.
	 */
	@Test
	public void render_placeholderWithDashesOrDots_handledAsKey() {
		NotificationTemplate template = createTemplate(SMS, null, null, "Dear ${user.name}, order ${order-id} ref ${meta.tx-ref} confirmed.");
		NotificationPreference preference = createPreference("+2348012345678", null, null);
		Map<String, String> context = Map.of(
			"user.name",  "Aisha",
			"order-id",   "ORD-9821",
			"meta.tx-ref", "TXN-ABC-123"
		);

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals("Dear Aisha, order ORD-9821 ref TXN-ABC-123 confirmed.", result.body());
	}
	
	/**
	 * Smoke test verifying that the engine correctly resolves a large number of placeholders
	 * in a single template string without producing incorrect output or throwing exceptions.
	 *
	 * <p>This is a <b>correctness-at-scale</b> test, not a performance benchmark. Wall-clock
	 * assertions are deliberately avoided because they are flaky across CI environments,
	 * hardware configurations, and GC pauses. The value of this test is proving that the
	 * single-pass {@code appendReplacement/appendTail} loop scales linearly and does not
	 * corrupt output when the number of placeholders is high.
	 *
	 * <p>The test generates a template body containing 1,000 unique placeholders
	 * ({@code ${key_0}} through {@code ${key_999}}), each mapped to a distinct value
	 * ({@code "val_0"} through {@code "val_999"}). After rendering, the test asserts that
	 * every single placeholder was correctly resolved by checking the full output string.
	 *
	 * <p>Additionally, the test verifies that no unresolved {@code ${...}} markers remain
	 * in the output — a secondary assertion that catches off-by-one errors in the loop
	 * or cursor-management bugs that might only surface at scale.
	 */
	@Test
	public void render_largeTemplate_performanceSmokeTest() {
		StringBuilder templateBody = new StringBuilder();
		HashMap<String, String> context = new HashMap<>();
		StringBuilder expectedBody = new StringBuilder();

		for (int i = 0; i < 1000; i++) {
			String key = "key_" + i;
			String value = "val_" + i;
			templateBody.append("${").append(key).append("} ");
			context.put(key, value);
			expectedBody.append(value).append(" ");
		}

		NotificationTemplate template = createTemplate(
				EMAIL, null, null, templateBody.toString()
		);
		NotificationPreference preference = createPreference(null, "user@example.com", null);

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals(expectedBody.toString(), result.body());
		// No unresolved placeholders should remain
		assertEquals(-1, result.body().indexOf("${"),
				"Unresolved placeholder found in output after rendering 1000 placeholders");
	}
	
	/**
	 * Verifies that Unicode characters in both template strings and context values are
	 * preserved exactly through the rendering pipeline — no mojibake, no character loss,
	 * no encoding corruption.
	 *
	 * <p>Beacon is designed for multi-channel notifications in a global context. Template
	 * strings and context values will routinely contain:
	 * <ul>
	 *     <li><b>Emoji</b> — multi-byte UTF-16 surrogate pairs (e.g., 🎉 is U+1F389,
	 *         encoded as two Java {@code char}s).</li>
	 *     <li><b>CJK ideographs</b> — Chinese/Japanese/Korean characters in the Basic
	 *         Multilingual Plane (e.g., 你好).</li>
	 *     <li><b>Cyrillic script</b> — used in Russian, Ukrainian, and other languages
	 *         (e.g., Привет).</li>
	 *     <li><b>Currency symbols</b> — Naira sign (₦), Euro (€), Yen (¥).</li>
	 * </ul>
	 *
	 * <p>The regex engine and {@code StringBuilder} used by {@code renderString} operate
	 * on Java's UTF-16 {@code char} sequences. This test verifies that no step in the
	 * pipeline — regex matching, group capture, {@code appendReplacement}, or
	 * {@code appendTail} — corrupts multi-byte characters.
	 *
	 * <p>The template contains Unicode in the literal text (emoji, Cyrillic), and the
	 * context values contain CJK and currency symbols, covering both paths.
	 */
	@Test
	public void render_unicodeCharacters_preserved() {
		NotificationTemplate template = createTemplate(PUSH, "🎉 Привет, ${name}!", "${greeting}", "Your balance: ${amount}. 谢谢 ${name}!");
		NotificationPreference preference = createPreference(null, null, "fcm-token-unicode");
		Map<String, String> context = Map.of(
			"name",     "田中太郎",
			"greeting", "こんにちは",
			"amount",   "¥10,000"
		);

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals("🎉 Привет, 田中太郎!", result.subject());
		assertEquals("こんにちは", result.title());
		assertEquals("Your balance: ¥10,000. 谢谢 田中太郎!", result.body());
		assertEquals("fcm-token-unicode", result.to());
	}
	
	/**
	 * Verifies that the {@code SMS} channel routes the destination to the preference's
	 * {@link NotificationPreference#phoneNumber()}.
	 *
	 * <p>The {@code extractDestination} method uses a {@code switch} expression on
	 * {@link NotificationChannel}: {@code case SMS -> preference.phoneNumber()}.
	 * This test provides a preference with all three destination fields populated
	 * (phone, email, push token) and asserts that only the phone number is selected.
	 * This proves the switch dispatches correctly and does not accidentally return
	 * the email or push token for SMS.
	 *
	 * <p>The template body has no placeholders — this test is focused exclusively on
	 * destination routing, not on template rendering. A static body isolates the
	 * assertion to the single behavior under test.
	 */
	@Test
	public void route_sms_usesPhoneNumber() {
		NotificationTemplate template = createTemplate(SMS, "Alert", null, "Your account was debited.");
		NotificationPreference preference = createPreference("+2349143768922", "user@example.com", "fcm-token-abc");
		Map<String, String> context = Map.of();

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals("+2349143768922", result.to());
		assertEquals(SMS, result.channel());
	}
	
	/**
	 * Verifies that the {@code EMAIL} channel routes the destination to the preference's
	 * {@link NotificationPreference#email()}.
	 *
	 * <p>The {@code extractDestination} method dispatches via
	 * {@code case EMAIL -> preference.email()}. This test provides a preference with all
	 * three destination fields populated and asserts that only the email address is
	 * returned. This proves the switch arm is correct and does not accidentally return
	 * the phone number or push token for email delivery.
	 *
	 * <p>Like {@link #route_sms_usesPhoneNumber()}, the template uses static content
	 * to isolate the routing assertion from template rendering concerns.
	 */
	@Test
	public void route_email_usesEmailAddress() {
		NotificationTemplate template = createTemplate(EMAIL, "Welcome", null, "Welcome aboard!");
		NotificationPreference preference = createPreference("+2349143768922", "kemi@beacon.io", "fcm-token-abc");
		Map<String, String> context = Map.of();

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals("kemi@beacon.io", result.to());
		assertEquals(EMAIL, result.channel());
	}
	
	/**
	 * Verifies that the {@code PUSH} channel routes the destination to the preference's
	 * {@link NotificationPreference#pushToken()}.
	 *
	 * <p>The {@code extractDestination} method dispatches via
	 * {@code case PUSH -> preference.pushToken()}. This test provides a preference with
	 * all three destination fields populated and asserts that only the push token is
	 * returned — not the phone number or email.
	 *
	 * <p>This completes the channel-routing triple alongside
	 * {@link #route_sms_usesPhoneNumber()} and {@link #route_email_usesEmailAddress()},
	 * ensuring every branch of the exhaustive {@code switch} expression is exercised.
	 */
	@Test
	public void route_push_usesDeviceToken() {
		NotificationTemplate template = createTemplate(PUSH, "Update", "New version", "Version 2.0 is available.");
		NotificationPreference preference = createPreference("+2349143768922", "user@example.com", "fcm-token-xyz-789");
		Map<String, String> context = Map.of();

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals("fcm-token-xyz-789", result.to());
		assertEquals(PUSH, result.channel());
	}
	
	/**
	 * Verifies that when the preference's destination field for the selected channel is
	 * {@code null}, the engine does not throw — it returns {@code null} as the destination,
	 * and template rendering proceeds normally.
	 *
	 * <p>This is a realistic scenario: a user may have registered their email but not their
	 * phone number, yet the system attempts to send an SMS notification. The preference
	 * object is non-null (the user exists), but {@code preference.phoneNumber()} returns
	 * {@code null}. The engine must not crash — it produces a {@link RenderedNotification}
	 * with {@code to = null}, and the delivery worker decides whether to skip, escalate, or
	 * fall back to another channel.
	 *
	 * <p>This test uses the SMS channel with a preference that has only an email address
	 * (phone and push token are {@code null}). Assertions verify:
	 * <ul>
	 *     <li>{@code to} is {@code null} — the missing phone number propagates.</li>
	 *     <li>Template fields are rendered correctly — the null destination does not
	 *         interfere with the rendering pipeline.</li>
	 *     <li>Channel is preserved — the output reflects the template's channel.</li>
	 * </ul>
	 */
	@Test
	public void route_missingDestination_throwsOrHandlesGracefully() {
		NotificationTemplate template = createTemplate(SMS, "Alert", null, "Hello ${name}, your account was debited.");
		NotificationPreference preference = createPreference(null, "user@example.com", null);
		Map<String, String> context = Map.of("name", "Tunde");

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertNull(result.to());
		assertEquals("Hello Tunde, your account was debited.", result.body());
		assertEquals(SMS, result.channel());
	}
	
	/**
	 * Verifies that the engine correctly handles a template where some fields are
	 * {@code null} and others contain placeholders — each field is rendered independently,
	 * and null fields do not interfere with non-null fields.
	 *
	 * <p>This is a realistic production pattern: an EMAIL notification typically has a
	 * subject and body but no title (title is semantically relevant only for PUSH).
	 * A PUSH notification might have a title and body but no subject. The engine must
	 * render each field through its own {@code renderString} call and must not let
	 * a null subject corrupt the body rendering or vice versa.
	 *
	 * <p>The template under test has:
	 * <ul>
	 *     <li>Subject: {@code null} — exercises the null guard, must produce {@code null}.</li>
	 *     <li>Title: {@code "Hello ${name}"} — non-null with a placeholder, must resolve.</li>
	 *     <li>Body: {@code null} — exercises the null guard again, must produce {@code null}.</li>
	 * </ul>
	 *
	 * <p>This pattern (null-nonnull-null) is the most adversarial mix because it interleaves
	 * null guards with actual rendering. If the engine shared state between field renderings
	 * (e.g., reused a StringBuilder without clearing), null fields could leak into non-null
	 * output.
	 */
	@Test
	public void render_nullSubjectTitleBody_mixOfNullAndNonNull() {
		NotificationTemplate template = createTemplate(PUSH, null, "Hello ${name}", null);
		NotificationPreference preference = createPreference(null, null, "fcm-token-mix");
		Map<String, String> context = Map.of("name", "Obinna");

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertNull(result.subject());
		assertEquals("Hello Obinna", result.title());
		assertNull(result.body());
		assertEquals("fcm-token-mix", result.to());
		assertEquals(PUSH, result.channel());
	}
	
	/**
	 * Verifies that extra keys in the context map — keys that have no corresponding
	 * {@code ${key}} placeholder in the template — are silently ignored without affecting
	 * the rendered output.
	 *
	 * <p>This is the expected behavior of a key-value template engine: the context is a
	 * superset of what the template needs. Hosts may build a shared context map containing
	 * all user attributes (name, email, phone, locale, tier, etc.) and pass it to every
	 * template. Each template picks the keys it needs and ignores the rest. If extra keys
	 * caused exceptions, warnings, or output corruption, hosts would be forced to build
	 * per-template context maps — an unnecessary coupling between the context-building
	 * layer and the template-authoring layer.
	 *
	 * <p>The test provides a context with five keys but a template that uses only two.
	 * Assertions verify:
	 * <ul>
	 *     <li>The two referenced placeholders resolve correctly.</li>
	 *     <li>The three extra keys ({@code "unused1"}, {@code "unused2"}, {@code "unused3"})
	 *         do not appear anywhere in the output.</li>
	 *     <li>No exception is thrown during rendering.</li>
	 * </ul>
	 */
	@Test
	public void render_contextWithExtraKeys_ignoredSafely() {
		NotificationTemplate template = createTemplate(EMAIL, "Hi ${name}", null, "Your order ${orderId} is ready.");
		NotificationPreference preference = createPreference(null, "user@example.com", null);
		Map<String, String> context = Map.of(
			"name",    "Ngozi",
			"orderId", "ORD-5500",
			"unused1", "extraValue1",
			"unused2", "extraValue2",
			"unused3", "extraValue3"
		);

		RenderedNotification result = templateEngine.render(template, preference, context);

		assertEquals("Hi Ngozi", result.subject());
		assertEquals("Your order ORD-5500 is ready.", result.body());
		// Extra keys must not leak into the output
		assertEquals(-1, result.subject().indexOf("extraValue"), "Extra context key leaked into subject");
		assertEquals(-1, result.body().indexOf("extraValue"), "Extra context key leaked into body");
	}
	
	private NotificationTemplate createTemplate(NotificationChannel channel, String subject, String title, String body) {
		return new NotificationTemplate(
			NotificationType.PAYMENT_RECEIPT,
			channel,
			subject,
			title,
			body
		);
	}
	
	private NotificationPreference createPreference(String phone, String email, String deviceToken) {
		return new NotificationPreference(
			UUID.randomUUID().toString(), // Dummy user ID
			email,
			phone,
			deviceToken,
			null,
			null,
			null,
			null
		);
	}
}
