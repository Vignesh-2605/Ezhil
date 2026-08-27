import sys
import os
import time
import fitz  # PyMuPDF
import logging

# Set up logging to console
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s: %(message)s")

# Add backend dir to path so we can import services
sys.path.append(os.path.abspath(os.path.dirname(__file__)))

from services import ocr_service

def main():
    pdf_path = "../Docs/ML final report.pdf"
    if not os.path.exists(pdf_path):
        pdf_path = "Docs/ML final report.pdf"
    
    if not os.path.exists(pdf_path):
        print(f"Error: PDF not found at {pdf_path}")
        return
        
    print(f"Opening PDF: {pdf_path}")
    doc = fitz.open(pdf_path)
    page = doc[0]  # First page
    print("Rendering page 1 to PNG bytes...")
    mat = fitz.Matrix(2.0, 2.0)
    pix = page.get_pixmap(matrix=mat, alpha=False)
    img_bytes = pix.tobytes("png")
    doc.close()
    
    print(f"Rendered image size: {len(img_bytes)} bytes")
    print("Testing OCR on rendered image page...")
    t0 = time.time()
    
    # Initialize reader and perform OCR
    text, confidence = ocr_service.ocr_image(img_bytes)
    
    duration = time.time() - t0
    print("-" * 50)
    print(f"OCR completed in {duration:.2f} seconds")
    print(f"Confidence: {confidence:.4f}")
    print(f"Extracted characters: {len(text)}")
    print("-" * 50)
    print("Extracted Text Sample:")
    print(text[:500])
    print("-" * 50)

if __name__ == "__main__":
    main()
