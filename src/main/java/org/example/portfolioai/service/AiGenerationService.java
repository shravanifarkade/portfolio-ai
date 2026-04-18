package org.example.portfolioai.service;

import org.example.portfolioai.dto.AiRequestDTO;
import org.example.portfolioai.dto.AiResponseDTO;
// Removed unused import
import org.example.portfolioai.dto.PortfolioRequestDTO;
import org.example.portfolioai.util.PromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Service
public class AiGenerationService {

    private final RestTemplate restTemplate;

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.model}")
    private String model;

    private final ObjectMapper objectMapper;

    public AiGenerationService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String generateBio(PortfolioRequestDTO request) {
        String prompt = PromptBuilder.buildBioPrompt(
                request.getRole(),
                request.getSkills(),
                request.getExperience(),
                request.getProjects());

        AiRequestDTO aiRequest = AiRequestDTO.builder()
                .model(model)
                .messages(Collections.singletonList(new AiRequestDTO.Message("user", prompt)))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<AiRequestDTO> entity = new HttpEntity<>(aiRequest, headers);

        try {
            ResponseEntity<AiResponseDTO> response = restTemplate.postForEntity(apiUrl, entity, AiResponseDTO.class);
            AiResponseDTO responseBody = response.getBody();
            if (responseBody != null && responseBody.getChoices() != null
                    && !responseBody.getChoices().isEmpty()) {
                return responseBody.getChoices().get(0).getMessage().getContent().trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to generate bio from AI service: " + e.getMessage());
        }

        return "Bio generation failed.";
    }

    public String generateWebsite(PortfolioRequestDTO request) {
        System.out.println("Generating website for template: " + request.getTemplate());
        String prompt = PromptBuilder.buildWebsitePrompt(
                request.getRole(),
                request.getSkills(),
                request.getExperience(),
                request.getProjects(),
                request.getName(),
                request.getEmail(),
                request.getPhone(),
                request.getLinkedin(),
                request.getGithub(),
                request.getThemeColor() != null ? request.getThemeColor() : "#4f46e5",
                request.getFontStyle() != null ? request.getFontStyle() : "Inter",
                request.getBackgroundStyle() != null ? request.getBackgroundStyle() : "Light",
                request.getSectionSpacing() != null ? request.getSectionSpacing() : "Comfortable",
                request.getCornerStyle() != null ? request.getCornerStyle() : "Rounded",
                request.getTemplate() != null ? request.getTemplate() : "Modern",
                request.getSectionOrder());

        // Request max tokens so AI produces rich, complete HTML
        // llama-3.3-70b-versatile on Groq supports up to 32,768 output tokens
        AiRequestDTO aiRequest = AiRequestDTO.builder()
                .model(model)
                .messages(java.util.Collections.singletonList(new AiRequestDTO.Message("user", prompt)))
                .maxTokens(16000)
                .temperature(0.7)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<AiRequestDTO> entity = new HttpEntity<>(aiRequest, headers);

        try {
            ResponseEntity<AiResponseDTO> response = restTemplate.postForEntity(apiUrl, entity, AiResponseDTO.class);
            AiResponseDTO responseBody = response.getBody();
            if (responseBody != null && responseBody.getChoices() != null
                    && !responseBody.getChoices().isEmpty()) {
                String content = responseBody.getChoices().get(0).getMessage().getContent().trim();
                // Clean up markdown code blocks if present (extra safety)
                if (content.startsWith("```html")) {
                    content = content.substring(7);
                } else if (content.startsWith("```")) {
                    content = content.substring(3);
                }
                if (content.endsWith("```")) {
                    content = content.substring(0, content.length() - 3);
                }

                String generatedHtml = content.trim();

                // Inject Profile Image if available
                if (request.getProfileImageBase64() != null && !request.getProfileImageBase64().isEmpty()) {
                    generatedHtml = generatedHtml.replace("{{PROFILE_IMAGE_SRC}}", request.getProfileImageBase64());
                }

                return generatedHtml;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to generate website from AI service: " + e.getMessage());
        }

        return "Website generation failed.";
    }

    /**
     * Generates a downloadable React + Vite project ZIP.
     *
     * Strategy: Reuse the proven HTML generator to get a high-quality portfolio page,
     * then wrap it in a minimal Vite scaffold. This avoids the brittle approach of asking
     * the LLM to return a multi-file JSON (which fails due to token limits and JSON escaping).
     */
    public java.util.Map<String, String> generateReactPortfolio(PortfolioRequestDTO request) {
        // Step 1: Generate the high-quality HTML portfolio (reuses the proven website generator)
        String generatedHtml = generateWebsite(request);

        // Step 2: Build a complete Vite + React scaffold with the HTML embedded
        java.util.Map<String, String> files = new java.util.LinkedHashMap<>();

        String name = request.getName() != null && !request.getName().isEmpty() ? request.getName() : "My Portfolio";
        String role = request.getRole() != null ? request.getRole() : "Developer";
        String themeColor = request.getThemeColor() != null ? request.getThemeColor() : "#4f46e5";

        // package.json
        files.put("package.json",
                "{\n" +
                "  \"name\": \"portfolio\",\n" +
                "  \"private\": true,\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"type\": \"module\",\n" +
                "  \"scripts\": {\n" +
                "    \"dev\": \"vite\",\n" +
                "    \"build\": \"vite build\",\n" +
                "    \"preview\": \"vite preview\"\n" +
                "  },\n" +
                "  \"dependencies\": {\n" +
                "    \"react\": \"^18.2.0\",\n" +
                "    \"react-dom\": \"^18.2.0\"\n" +
                "  },\n" +
                "  \"devDependencies\": {\n" +
                "    \"@vitejs/plugin-react\": \"^4.0.0\",\n" +
                "    \"vite\": \"^5.0.0\"\n" +
                "  }\n" +
                "}");

        // vite.config.js
        files.put("vite.config.js",
                "import { defineConfig } from 'vite';\n" +
                "import react from '@vitejs/plugin-react';\n\n" +
                "export default defineConfig({\n" +
                "  plugins: [react()],\n" +
                "});\n");

        // index.html (Vite entry point)
        files.put("index.html",
                "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "  <head>\n" +
                "    <meta charset=\"UTF-8\" />\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n" +
                "    <title>" + name + " | " + role + "</title>\n" +
                "  </head>\n" +
                "  <body>\n" +
                "    <div id=\"root\"></div>\n" +
                "    <script type=\"module\" src=\"/src/main.jsx\"></script>\n" +
                "  </body>\n" +
                "</html>\n");

        // src/main.jsx
        files.put("src/main.jsx",
                "import React from 'react';\n" +
                "import ReactDOM from 'react-dom/client';\n" +
                "import App from './App.jsx';\n" +
                "import './index.css';\n\n" +
                "ReactDOM.createRoot(document.getElementById('root')).render(\n" +
                "  <React.StrictMode>\n" +
                "    <App />\n" +
                "  </React.StrictMode>\n" +
                ");\n");

        // src/index.css (minimal reset)
        files.put("src/index.css",
                "*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }\n" +
                "body { min-height: 100vh; }\n" +
                "#root { display: flex; flex-direction: column; min-height: 100vh; }\n");

        // Escape the HTML for embedding in a JS template literal
        String escapedHtml = generatedHtml
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("${", "\\${");

        // src/App.jsx — embeds the AI-generated HTML in an iframe for instant preview
        files.put("src/App.jsx",
                "import React from 'react';\n\n" +
                "// AI-generated portfolio HTML\n" +
                "const portfolioHtml = `" + escapedHtml + "`;\n\n" +
                "function App() {\n" +
                "  const handleDownload = () => {\n" +
                "    const blob = new Blob([portfolioHtml], { type: 'text/html' });\n" +
                "    const url = URL.createObjectURL(blob);\n" +
                "    const a = document.createElement('a');\n" +
                "    a.href = url;\n" +
                "    a.download = 'portfolio.html';\n" +
                "    a.click();\n" +
                "    URL.revokeObjectURL(url);\n" +
                "  };\n\n" +
                "  return (\n" +
                "    <div style={{ fontFamily: 'sans-serif', background: '#0f172a', minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>\n" +
                "      {/* Toolbar */}\n" +
                "      <div style={{ padding: '10px 24px', background: '#1e293b', display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #334155', flexShrink: 0 }}>\n" +
                "        <div>\n" +
                "          <span style={{ color: '#f8fafc', fontWeight: 700, fontSize: '16px' }}>🚀 " + name + "'s Portfolio</span>\n" +
                "          <span style={{ color: '#64748b', fontSize: '13px', marginLeft: '16px' }}>Preview — or open <code style={{color:'#60a5fa'}}>portfolio.html</code> directly</span>\n" +
                "        </div>\n" +
                "        <button\n" +
                "          onClick={handleDownload}\n" +
                "          style={{ background: '" + themeColor + "', color: '#fff', border: 'none', borderRadius: '8px', padding: '8px 20px', cursor: 'pointer', fontWeight: 600, fontSize: '14px' }}\n" +
                "        >\n" +
                "          ⬇ Download portfolio.html\n" +
                "        </button>\n" +
                "      </div>\n" +
                "      {/* Portfolio Preview */}\n" +
                "      <iframe\n" +
                "        srcDoc={portfolioHtml}\n" +
                "        style={{ flex: 1, border: 'none', width: '100%', minHeight: 'calc(100vh - 57px)' }}\n" +
                "        title=\"Portfolio Preview\"\n" +
                "      />\n" +
                "    </div>\n" +
                "  );\n" +
                "}\n\n" +
                "export default App;\n");

        // README.md
        files.put("README.md",
                "# " + name + " — Portfolio\n\n" +
                "Generated by **CodeFolio AI** 🚀\n\n" +
                "## Quick Start (React Dev Server)\n\n" +
                "```bash\n" +
                "npm install\n" +
                "npm run dev\n" +
                "```\n\n" +
                "Open http://localhost:5173 to see your portfolio with a live toolbar.\n\n" +
                "## Standalone HTML (No build required)\n\n" +
                "Open `portfolio.html` directly in any browser — no server needed.\n\n" +
                "Or click **⬇ Download portfolio.html** in the React preview toolbar.\n");

        // portfolio.html — the raw AI-generated HTML, ready to open directly
        files.put("portfolio.html", generatedHtml);

        return files;
    }

    public org.example.portfolioai.dto.AnalysisResponseDTO analyzeProfile(PortfolioRequestDTO request) {
        String prompt = PromptBuilder.buildAnalysisPrompt(
                request.getRole(),
                request.getSkills(),
                request.getExperience(),
                request.getProjects());

        AiRequestDTO aiRequest = AiRequestDTO.builder()
                .model(model)
                .messages(Collections.singletonList(new AiRequestDTO.Message("user", prompt)))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<AiRequestDTO> entity = new HttpEntity<>(aiRequest, headers);

        try {
            ResponseEntity<AiResponseDTO> response = restTemplate.postForEntity(apiUrl, entity, AiResponseDTO.class);
            AiResponseDTO responseBody = response.getBody();
            if (responseBody != null && responseBody.getChoices() != null
                    && !responseBody.getChoices().isEmpty()) {
                String jsonResponse = responseBody.getChoices().get(0).getMessage().getContent().trim();
                // Basic cleanup if AI adds markdown
                jsonResponse = jsonResponse.replace("```json", "").replace("```", "").trim();
                return objectMapper.readValue(jsonResponse, org.example.portfolioai.dto.AnalysisResponseDTO.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to analyze profile: " + e.getMessage());
        }
        return new org.example.portfolioai.dto.AnalysisResponseDTO();
    }

    public PortfolioRequestDTO parseResume(String resumeText) {
        String prompt = "Extract the following details from the resume text below and return them in strictly valid JSON format with keys: 'name', 'email', 'phone', 'linkedin', 'github', 'role', 'skills', 'experience', 'projects'.\n"
                +
                "IMPORTANT FORMATTING RULES: \n" +
                "- 'skills': Must be a strictly COMMA-SEPARATED string (e.g., 'Java, Python, React, SQL'). Do NOT group them or use other delimiters.\n"
                +
                "- 'experience': Must be a formatted string using BULLET POINTS (•) for each role. Include Company, Role, and Dates if available. Use newlines to separate bullets.\n"
                +
                "- 'projects': Must be a formatted string using BULLET POINTS (•) for each project. Include Project Name and a brief description. Use newlines to separate bullets.\n"
                +
                "- 'name', 'email', 'phone', 'linkedin', 'github': Extract if available, otherwise return empty strings.\n"
                +
                "- Do NOT return JSON arrays or objects for any fields. Return strings only.\n" +
                "Resume Text:\n" +
                resumeText + "\n" +
                "\n" +
                "JSON Output (do not include markdown formatting):";

        AiRequestDTO aiRequest = AiRequestDTO.builder()
                .model(model)
                .messages(Collections.singletonList(new AiRequestDTO.Message("user", prompt)))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<AiRequestDTO> entity = new HttpEntity<>(aiRequest, headers);

        try {
            ResponseEntity<AiResponseDTO> response = restTemplate.postForEntity(apiUrl, entity, AiResponseDTO.class);
            AiResponseDTO responseBody = response.getBody();
            if (responseBody != null && responseBody.getChoices() != null
                    && !responseBody.getChoices().isEmpty()) {
                String content = responseBody.getChoices().get(0).getMessage().getContent().trim();
                System.out.println("AI RAW RESPONSE: " + content);

                // Cleanup JSON string
                if (content.startsWith("```json")) {
                    content = content.substring(7);
                } else if (content.startsWith("```")) {
                    content = content.substring(3);
                }
                if (content.endsWith("```")) {
                    content = content.substring(0, content.length() - 3);
                }

                return objectMapper.readValue(content.trim(), PortfolioRequestDTO.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to parse resume with AI: " + e.getMessage());
        }

        throw new RuntimeException("AI response was empty");
    }
}
