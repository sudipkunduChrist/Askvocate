import json

notebook_path = 'notebooks/02_vector_embeddings_exploration.ipynb'

with open(notebook_path, 'r', encoding='utf-8') as f:
    nb = json.load(f)

for cell in nb['cells']:
    source = ''.join(cell.get('source', []))
    if "q['user_query']" in source:
        source = source.replace("q['user_query']", "q['query_text']")
        source = source.replace("q['expected_domain']", "q['legal_domain']")
        cell['source'] = [line + '\n' for line in source.split('\n')]
        cell['source'][-1] = cell['source'][-1].rstrip('\n')

with open(notebook_path, 'w', encoding='utf-8') as f:
    json.dump(nb, f, indent=1, ensure_ascii=False)

print("Updated 02_vector_embeddings_exploration.ipynb with correct column names (query_text, legal_domain)!")
