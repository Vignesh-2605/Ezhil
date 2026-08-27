import fitz
import PIL.Image
import numpy as np
import easyocr
import time
import io
import torch
import os
from concurrent.futures import ThreadPoolExecutor

# Global reader initialized once
reader = None

def init_reader():
    global reader
    # Limit PyTorch threads to prevent CPU thrashing
    torch.set_num_threads(1)
    # Fix Pillow 10+ compatibility
    PIL.Image.ANTIALIAS = PIL.Image.Resampling.LANCZOS
    reader = easyocr.Reader(['en'], gpu=False)

def process_page(args):
    page_num, pdf_path = args
    t0 = time.time()
    
    try:
        # Open doc inside thread to avoid fitz concurrency issues
        doc = fitz.open(pdf_path)
        page = doc[page_num]
        
        # Render page to image
        pix = page.get_pixmap(matrix=fitz.Matrix(2.0, 2.0))
        img_bytes = pix.tobytes("png")
        img = PIL.Image.open(io.BytesIO(img_bytes)).convert("RGB")
        doc.close()
        
        # Run OCR
        res = reader.readtext(np.array(img), detail=0)
        page_text = "\n".join(res)
        
        duration = time.time() - t0
        print(f"Page {page_num + 1} processed in {duration:.2f}s (lines: {len(res)})")
        return page_num, f"--- PAGE {page_num + 1} ---\n{page_text}"
    except Exception as e:
        print(f"Error on page {page_num + 1}: {e}")
        return page_num, f"--- PAGE {page_num + 1} ---\n[ERROR: {e}]"

def main():
    pdf_path = r"../Docs/ML final report.pdf"
    output_path = r"../Docs/ML_final_report_ocr.txt"

    print("Starting full PDF OCR extraction in parallel...")
    t_start = time.time()

    doc = fitz.open(pdf_path)
    page_count = doc.page_count
    doc.close()
    print(f"Total pages: {page_count}")

    print("Initializing EasyOCR...")
    init_reader()

    args_list = [(i, pdf_path) for i in range(page_count)]
    
    # Use 8 workers since we have 16 cores
    num_workers = 8
    print(f"Running with {num_workers} parallel workers...")
    
    ocr_results = [None] * page_count
    
    with ThreadPoolExecutor(max_workers=num_workers) as executor:
        results = executor.map(process_page, args_list)
        for page_num, page_text in results:
            ocr_results[page_num] = page_text

    # Write output to file
    with open(output_path, "w", encoding="utf-8") as f:
        f.write("\n\n".join(ocr_results))

    print(f"Finished! Total time taken: {time.time() - t_start:.2f}s")
    print(f"Results saved to: {os.path.abspath(output_path)}")

if __name__ == "__main__":
    main()
