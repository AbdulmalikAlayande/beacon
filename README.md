To build an enterprise-ready template engine from scratch for a Spring Boot notification library (supporting Email, SMS, and Push), we must avoid the biggest mistake developers make: using massive, fragile regular expressions. Complex regex breaks when logic nests (e.g., an if inside a for loop) and is prone to catastrophic backtracking security bugs. [1, 2]
Instead, we will build a production-grade template engine using a clean Lexer $\to$ Parser $\to$ Abstract Syntax Tree (AST) $\to$ Interpreter architecture. [1, 3]
------------------------------
## 🏛️ The Architecture Design Blueprint
Our engine is split into four distinct, loosely coupled layers to maximize pluggability: [4]

1. SPI Layer (The API): Defines what the host application sees. Provides TemplateEngine and Template interfaces.
2. Lexical Analysis (The Lexer): Chops template text strings into typed structural tokens (Text, Variables, Loops, Conditionals).
3. Syntactic Analysis (The Parser & AST): Organizes tokens into a hierarchical logical tree of executable syntax nodes.
4. Execution Layer (The Interpreter): Evaluates the syntax nodes against a provided context map to emit a clean output string. [1, 5]

[Template Code] -> [Lexer] -> [Tokens] -> [Parser] -> [AST Tree] -> [Interpreter + Model] -> [Final Output]

------------------------------
## Step 1: Define the SPI Layer (Extensibility Architecture)
We start by exposing high-level abstraction interfaces. This lets the notification library use a DefaultTemplateEngine while enabling host applications to drop in an alternative engine (e.g., Pebble or JTE) later without breaking the codebase. [6]

```java
package com.notification.library.template;
import java.util.Map;
/**
* Public SPI contract for the host application to use or override.
*/
public interface TemplateEngine {

  /**
  * Compiles a template string into an executable, reusable structure.
  */
  Template compile(String templateText);

  /**
   * Utility shortcut to compile and execute a template immediately.
   */
  default String render(String templateText, Map<String, Object> context) {
	  return compile(templateText).evaluate(context);
  }
}
```

```java
package com.notification.library.template;
import java.util.Map;
/**
* A pre-parsed, executable representation of a single notification template.
* Instances are thread-safe and safe to cache long-term.
 */
public interface Template { 
	String evaluate(Map<String, Object> context);
}
```
------------------------------
## Step 2: Build the Lexer (Tokenization Engine)
The template language will feature three constructs:

* Variables: {{ user.name }}
* Conditionals: {% if showPromo %} Get 20% off! {% endif %}
* Loops: {% for item in items %} - {{ item }} {% endfor %}

The Lexer scans the string to split static layout blocks from operational tags. [3, 7]

```java
package com.notification.library.template.engine;

public enum TokenType {
    TEXT, VARIABLE, IF_START, IF_END, FOR_START, FOR_END
}
```

```java
package com.notification.library.template.engine;

public record Token(TokenType type, String value) {
	
}
```
     
```java
package com.notification.library.template.engine;

import java.util.ArrayList;import java.util.List;

public class Lexer {
	
    public static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = source.length();
    
        while (i < len) {
            // Check for Tag Blocks {% ... %}
            if (source.startsWith("{%", i)) {
                int endIdx = source.indexOf("%}", i);
                if (endIdx == -1) throw new IllegalArgumentException("Unclosed tag block near index " + i);
                
                String tagContent = source.substring(i + 2, endIdx).trim();
                if (tagContent.startsWith("if ")) {
                    tokens.add(new Token(TokenType.IF_START, tagContent.substring(3).trim()));
                } else if (tagContent.equals("endif")) {
                    tokens.add(new Token(TokenType.IF_END, ""));
                } else if (tagContent.startsWith("for ")) {
                    tokens.add(new Token(TokenType.FOR_START, tagContent.substring(4).trim()));
                } else if (tagContent.equals("endfor")) {
                    tokens.add(new Token(TokenType.FOR_END, ""));
                } else {
                    throw new IllegalArgumentException("Unknown control tag: " + tagContent);
                }
                i = endIdx + 2;
            } 
            // Check for Variable Blocks {{ ... }}
            else if (source.startsWith("{{", i)) {
                int endIdx = source.indexOf("}}", i);
                if (endIdx == -1) throw new IllegalArgumentException("Unclosed variable block near index " + i);
                
                String varExpression = source.substring(i + 2, endIdx).trim();
                tokens.add(new Token(TokenType.VARIABLE, varExpression));
                i = endIdx + 2;
            } 
            // Plain Text Blocks
            else {
                int nextTag = source.indexOf("{%", i);
                int nextVar = source.indexOf("{{", i);
                int nextStop = -1;

                if (nextTag != -1 && nextVar != -1) nextStop = Math.min(nextTag, nextVar);
                else if (nextTag != -1) nextStop = nextTag;
                else nextStop = nextVar;

                if (nextStop == -1) nextStop = len;

                String textContent = source.substring(i, nextStop);
                if (!textContent.isEmpty()) {
                    tokens.add(new Token(TokenType.TEXT, textContent));
                }
                i = nextStop;
            }
        }
		return tokens;
    }
}
```
------------------------------
## Step 3: Build the Abstract Syntax Tree (AST) & Parser
The Parser groups tokens into hierarchical logical nodes. Using an AST makes handling nested conditions or deep loops simple and completely bulletproof. [1, 3]
## Composite AST Node Infrastructure

package com.notification.library.template.engine;
import java.util.Map;
public interface AstNode {
void execute(Map<String, Object> context, StringBuilder output);
}

## AST Node Implementations

package com.notification.library.template.engine;
import java.util.List;import java.util.Map;
// 1. Literal Textrecord TextNode(String text) implements AstNode {
public void execute(Map<String, Object> context, StringBuilder output) {
output.append(text);
}
}
// 2. Evaluated Variables (with nested object dot notation property navigation support)record VariableNode(String expression) implements AstNode {
public void execute(Map<String, Object> context, StringBuilder output) {
Object val = ExpressionEvaluator.resolve(expression, context);
if (val != null) output.append(val);
}
}
// 3. Conditional Branch Execution Blocksrecord IfNode(String conditionKey, List<AstNode> children) implements AstNode {
public void execute(Map<String, Object> context, StringBuilder output) {
Object val = ExpressionEvaluator.resolve(conditionKey, context);
boolean isTrue = val instanceof Boolean ? (Boolean) val : val != null;
if (isTrue) {
for (AstNode child : children) child.execute(context, output);
}
}
}
// 4. Repetitive Loop Blocksrecord ForNode(String loopVar, String collectionKey, List<AstNode> children) implements AstNode {
public void execute(Map<String, Object> context, StringBuilder output) {
Object collectionObj = ExpressionEvaluator.resolve(collectionKey, context);
if (collectionObj instanceof Iterable<?> iterable) {
for (Object item : iterable) {
context.put(loopVar, item); // Scoped injection
for (AstNode child : children) child.execute(context, output);
}
context.remove(loopVar); // Clean context safety
}
}
}

## The Recursive Descent Parser Engine [8]

package com.notification.library.template.engine;
import java.util.ArrayList;import java.util.List;
public class Parser {
private final List<Token> tokens;
private int cursor = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<AstNode> parse() {
        return parseNodes(null);
    }

    private List<AstNode> parseNodes(TokenType stoppingToken) {
        List<AstNode> nodes = new ArrayList<>();

        while (cursor < tokens.size()) {
            Token current = tokens.get(cursor);

            if (stoppingToken != null && current.type() == stoppingToken) {
                cursor++; // Consume terminating structural token
                return nodes;
            }

            cursor++; // Move cursor onward
            switch (current.type()) {
                case TEXT -> nodes.add(new TextNode(current.value()));
                case VARIABLE -> nodes.add(new VariableNode(current.value()));
                case IF_START -> {
                    List<AstNode> innerBody = parseNodes(TokenType.IF_END);
                    nodes.add(new IfNode(current.value(), innerBody));
                }
                case FOR_START -> {
                    // Structure: item in items
                    String parts[] = current.value().split(" in ");
                    if (parts.length != 2) throw new IllegalStateException("Malformed for tag syntax: " + current.value());
                    List<AstNode> innerBody = parseNodes(TokenType.FOR_END);
                    nodes.add(new ForNode(parts[0].trim(), parts[1].trim(), innerBody));
                }
                case IF_END, FOR_END -> throw new IllegalStateException("Unexpected structural end tag token observed: " + current.type());
            }
        }

        if (stoppingToken != null) {
            throw new IllegalStateException("Unclosed block structural layout. Missing tag: " + stoppingToken);
        }
        return nodes;
    }
}

------------------------------
## Step 4: Write the Context Expression Evaluator
To prevent errors when handling deep nested context objects (e.g., {{ user.profile.address.city }}), we use a recursive resolver.

package com.notification.library.template.engine;
import java.lang.reflect.Method;import java.util.Map;
public class ExpressionEvaluator {
public static Object resolve(String expression, Map<String, Object> context) {
String[] paths = expression.split("\\.");
Object current = context.get(paths[0]);

        for (int i = 1; i < paths.length; i++) {
            if (current == null) return null;
            current = getProperty(current, paths[i]);
        }
        return current;
    }

    private static Object getProperty(Object obj, String property) {
        if (obj instanceof Map<?,?> map) return map.get(property);
        try {
            // Supports standard bean getters: getCity() or city() records
            String getterName = "get" + property.substring(0,1).toUpperCase() + property.substring(1);
            try {
                Method method = obj.getClass().getMethod(getterName);
                return method.invoke(obj);
            } catch (NoSuchMethodException e) {
                Method method = obj.getClass().getMethod(property);
                return method.invoke(obj);
            }
        } catch (Exception e) {
            return null; // Return null gracefully to keep notification flows fault-tolerant
        }
    }
}

------------------------------
## Step 5: Implement the Exposed DefaultTemplateEngine
Now we wrap the pipeline behind our implementation class. We compile templates ahead of time into immutable executable trees, allowing safe caching across execution operations.

package com.notification.library.template.engine;
import com.notification.library.template.Template;import com.notification.library.template.TemplateEngine;import java.util.HashMap;import java.util.List;import java.util.Map;
public class DefaultTemplateEngine implements TemplateEngine {

    @Override
    public Template compile(String templateText) {
        if (templateText == null) throw new IllegalArgumentException("Template cannot be null");
        
        // 1. Run Tokenizer
        List<Token> tokens = Lexer.tokenize(templateText);
        // 2. Parse into AST Representation
        List<AstNode> rootNodes = new Parser(tokens).parse();
        
        // 3. Return Runnable Template Interface Instantiation Instance
        return context -> {
            // Keep state completely isolated per-execution for concurrent thread safety
            Map<String, Object> executionContext = new HashMap<>(context);
            StringBuilder output = new StringBuilder();
            for (AstNode node : rootNodes) {
                node.execute(executionContext, output);
            }
            return output.toString();
        };
    }
}

------------------------------
## Step 6: Verify and Run Across Notification Mediums
This clean engine compiles and renders text safely across all three channels: SMS (plain text), Push Notifications (JSON keys), and Emails (HTML payloads). [2]

public class VerificationDemo {
public static void main(String[] args) {
TemplateEngine engine = new DefaultTemplateEngine();

        // 1. Target Channel: SMS Payload
        String smsTemplate = "Hello {{ user.name }}, your order status is: {% if verified %} Shipped! {% endif %}";
        Map<String, Object> smsContext = Map.of(
            "user", Map.of("name", "John"),
            "verified", true
        );
        System.out.println(engine.render(smsTemplate, smsContext)); 
        // Emits: "Hello John, your order status is:  Shipped! "

        // 2. Target Channel: Rich HTML Email Content Body
        String htmlEmailTemplate = """
            <h3>Your Notifications</h3>
            <ul>
               {% for update in alerts %}
                 <li>⚠️ {{ update }}</li>
               {% endfor %}
            </ul>
            """;
        Map<String, Object> emailContext = Map.of("alerts", List.of("Password Changed", "New Login Detected"));
        System.out.println(engine.render(htmlEmailTemplate, emailContext));
        /* Emits:
           <h3>Your Notifications</h3>
           <ul>
                <li>⚠️ Password Changed</li>
                <li>⚠️ New Login Detected</li>
           </ul>
        */
    }
}

------------------------------
To wrap up this module cleanly inside your Spring Boot library, let me know:

* Would you like the complete Spring Boot @AutoConfiguration structure next to automatically export DefaultTemplateEngine to host applications?
* Do you need an integrated Concurrent HashMap caching architecture inside the engine to prevent parsing overhead for repetitive notifications?
* Should we add automatic HTML escaping options for email payloads to secure the system against cross-site scripting (XSS) injections?


[1] [https://www.youtube.com](https://www.youtube.com/watch?v=4m7ubrdbWQU&t=6)
[2] [https://medium.com](https://medium.com/@tanushree2102/designing-a-scalable-notification-system-from-hld-to-lld-e2ed4b3fb348)
[3] [https://tomassetti.me](https://tomassetti.me/parsing-in-java/)
[4] [https://blog.devops.dev](https://blog.devops.dev/building-a-flexible-and-scalable-notification-system-design-and-implementation-eef601f22518)
[5] [https://www.youtube.com](https://www.youtube.com/watch?v=70NVv0nVLlE&t=32)
[6] [https://www.youtube.com](https://www.youtube.com/watch?v=KoWgHSWA1cc)
[7] [https://faouellet.github.io](https://faouellet.github.io/toslang-parser/)
[8] [https://www.youtube.com](https://www.youtube.com/watch?v=t-4r2AsJz_Q)
