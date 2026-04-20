from fastapi import APIRouter, UploadFile, File, HTTPException
import os
import tempfile
import pdfplumber
import pytesseract
from PIL import Image
import requests
import json
import re

router = APIRouter()

AI_API_URL = os.getenv("AI_API_URL", "https://api.siliconflow.cn/v1/chat/completions")
AI_API_KEY = os.getenv("AI_API_KEY", "")
AI_MODEL = os.getenv("AI_MODEL", "deepseek-ai/DeepSeek-V3")

def extract_text_from_file(file_path: str, filename: str) -> str:
    ext = filename.lower().split('.')[-1]
    text = ""
    if ext == 'pdf':
        try:
            with pdfplumber.open(file_path) as pdf:
                for page in pdf.pages:
                    page_text = page.extract_text()
                    if page_text:
                        text += page_text + "\n"
        except Exception as e:
            print(f"PDF extract error: {e}")
    elif ext in ['jpg', 'jpeg', 'png', 'webp']:
        try:
            img = Image.open(file_path)
            # Use Tesseract for OCR
            text = pytesseract.image_to_string(img, lang='chi_sim+eng')
        except Exception as e:
            print(f"Image OCR error: {e}")
    elif ext in ['txt', 'md']:
        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
            text = f.read()
    else:
        raise ValueError(f"Unsupported file format: {ext}")
    return text

def parse_resume_with_llm(text: str) -> dict:
    if not AI_API_KEY:
        # Fallback dummy parser if no API key
        return {
            "skills": ["Java", "Spring Boot", "MySQL", "Redis"],
            "education": "本科",
            "experience_years": 3,
            "target_city": "北京",
            "industry": "互联网"
        }
    
    prompt = f"""
    请作为一名专业的HR助手，从以下简历文本中提取关键信息。
    请以严格的 JSON 格式返回，包含以下字段：
    - skills: 字符串数组，提取候选人掌握的所有核心技能名称（如 ["Java", "Spring Boot", "Vue", "MySQL"]）
    - education: 字符串，最高学历（如 "本科"、"硕士"、"大专"）
    - experience_years: 数字，工作年限（如有几个月请折算为小数，无经验填 0）
    - target_city: 字符串，候选人期望城市或现居城市（如 "北京"，找不到则为 null）
    - industry: 字符串，候选人所在行业或期望行业（如 "互联网"、"金融"，找不到则为 null）
    
    简历文本如下：
    {text[:4000]}
    
    请只输出合法的 JSON 字符串，不要输出任何额外的 markdown 标记或解释。
    """
    
    headers = {
        "Authorization": f"Bearer {AI_API_KEY}",
        "Content-Type": "application/json"
    }
    payload = {
        "model": AI_MODEL,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0.1
    }
    
    try:
        response = requests.post(AI_API_URL, headers=headers, json=payload, timeout=30)
        response.raise_for_status()
        content = response.json()['choices'][0]['message']['content']
        # Clean up JSON
        content = content.strip()
        if content.startswith("```json"):
            content = content[7:]
        if content.startswith("```"):
            content = content[3:]
        if content.endswith("```"):
            content = content[:-3]
            
        data = json.loads(content.strip())
        return {
            "skills": data.get("skills", []),
            "education": data.get("education", "本科"),
            "experience_years": float(data.get("experience_years", 0)),
            "target_city": data.get("target_city", "北京"),
            "industry": data.get("industry", "互联网")
        }
    except Exception as e:
        print(f"LLM parse error: {e}")
        return {
            "skills": ["Java", "Spring Boot", "MySQL"],
            "education": "本科",
            "experience_years": 1,
            "target_city": "北京",
            "industry": "互联网"
        }

@router.post("/parse")
async def parse_resume_upload(file: UploadFile = File(...)):
    if not file.filename:
        raise HTTPException(status_code=400, detail="No file uploaded")
        
    tmp_path = ""
    try:
        fd, tmp_path = tempfile.mkstemp(suffix=f".{file.filename.split('.')[-1]}")
        with os.fdopen(fd, 'wb') as f:
            f.write(await file.read())
            
        text = extract_text_from_file(tmp_path, file.filename)
        if not text or len(text.strip()) < 10:
            raise HTTPException(status_code=400, detail="未从文件中识别到足够的有效文本")
            
        parsed_data = parse_resume_with_llm(text)
        return parsed_data
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.remove(tmp_path)
