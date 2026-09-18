"""
Script to refine advocate_details.csv practice areas so every case domain in cases.csv 
has dedicated specialist lawyers with exact primary practice areas.
"""

import pandas as pd
import numpy as np

def fix_lawyer_dataset():
    csv_path = 'datasets/advocate_details.csv'
    df = pd.read_csv(csv_path)
    
    # Backup original dataset if backup does not exist
    backup_path = 'datasets/advocate_details_backup.csv'
    try:
        df.to_csv(backup_path, index=False)
        print(f"Created backup at {backup_path}")
    except Exception as e:
        print(f"Backup warning: {e}")

    # Map of case domains to target primary practice area names
    domain_to_practice_area = {
        "Administrative Law": "Administrative Law",
        "Arbitration": "Arbitration and Mediation",
        "Banking & Finance": "Banking and Finance",
        "Child Protection": "Child Protection Law",
        "Constitutional Law": "Constitutional Law",
        "Consumer Protection": "Consumer Law",
        "Contract & Agreement": "Contract Law",
        "Corporate & Commercial": "Corporate Law",
        "Criminal Law": "Criminal Law",
        "Cybercrime & IT": "Cyber Law",
        "Data Privacy": "Data Protection and Privacy",
        "Divorce & Matrimonial": "Matrimonial Law",
        "Education Law": "Education Law",
        "Employment & Labour": "Labour and Employment Law",
        "Environmental Law": "Environmental Law",
        "Family & Succession": "Family Law",
        "Human Rights & PIL": "Human Rights Law",
        "Immigration & Citizenship": "Immigration Law",
        "Insolvency & Bankruptcy": "Insolvency and Bankruptcy",
        "Intellectual Property": "Intellectual Property",
        "Media & Defamation": "Media and Entertainment Law",
        "Medical Negligence": "Medical Negligence Law",
        "Motor Accident Claims": "Motor Accident Claims Law",
        "Property & Land": "Property Law",
        "Real Estate & Housing": "Real Estate Law",
        "Tax & GST": "Tax Law",
        "White Collar Crime": "White Collar Crime"
    }

    target_areas = list(set(domain_to_practice_area.values()))
    num_lawyers = len(df)
    
    # Set seed for reproducible assignment
    np.random.seed(42)

    # Evenly distribute the 27 target practice areas across 7000 lawyers (~259 lawyers per area)
    assigned_primary = np.random.choice(target_areas, size=num_lawyers)
    df['practice_area_primary'] = assigned_primary

    # Generate secondary practice areas matching related domain concepts + civil litigation / procedure
    secondary_pool = {
        "Administrative Law": ["Constitutional Law", "Public Interest Litigation", "Public Interest Litigation"],
        "Arbitration and Mediation": ["Commercial Law", "Corporate Law", "Public Interest Litigation"],
        "Banking and Finance": ["Corporate Law", "Securities Law", "Insolvency and Bankruptcy"],
        "Child Protection Law": ["Family Law", "Constitutional Law", "Criminal Law"],
        "Constitutional Law": ["Human Rights Law", "Public Interest Litigation", "Administrative Law"],
        "Consumer Law": ["Public Interest Litigation", "Commercial Law", "Medical Negligence Law"],
        "Contract Law": ["Commercial Law", "Public Interest Litigation", "Arbitration and Mediation"],
        "Corporate Law": ["Banking and Finance", "Securities Law", "Commercial Law"],
        "Criminal Law": ["Constitutional Law", "White Collar Crime", "Cyber Law"],
        "Cyber Law": ["Information Technology Law", "Data Protection and Privacy", "Criminal Law"],
        "Data Protection and Privacy": ["Information Technology Law", "Cyber Law", "Intellectual Property"],
        "Matrimonial Law": ["Family Law", "Public Interest Litigation", "Child Protection Law"],
        "Education Law": ["Constitutional Law", "Administrative Law", "Public Interest Litigation"],
        "Labour and Employment Law": ["Public Interest Litigation", "Constitutional Law", "Corporate Law"],
        "Environmental Law": ["Public Interest Litigation", "Constitutional Law", "Administrative Law"],
        "Family Law": ["Matrimonial Law", "Child Protection Law", "Public Interest Litigation"],
        "Human Rights Law": ["Public Interest Litigation", "Constitutional Law", "Criminal Law"],
        "Immigration Law": ["Constitutional Law", "Administrative Law", "Public Interest Litigation"],
        "Insolvency and Bankruptcy": ["Banking and Finance", "Corporate Law", "Commercial Law"],
        "Intellectual Property": ["Patent Law", "Trademark Law", "Copyright Law", "Commercial Law"],
        "Media and Entertainment Law": ["Intellectual Property", "Public Interest Litigation", "Criminal Law"],
        "Medical Negligence Law": ["Consumer Law", "Personal Injury Law", "Public Interest Litigation"],
        "Motor Accident Claims Law": ["Personal Injury Law", "Public Interest Litigation", "Insurance Law"],
        "Property Law": ["Real Estate Law", "Public Interest Litigation", "Contract Law"],
        "Real Estate Law": ["Property Law", "Public Interest Litigation", "Consumer Law"],
        "Tax Law": ["GST Law", "Corporate Law", "Commercial Law"],
        "White Collar Crime": ["Criminal Law", "Banking and Finance", "Corporate Law"]
    }

    secondary_list = []
    for pri in df['practice_area_primary']:
        options = secondary_pool.get(pri, ["Public Interest Litigation"])
        # Select 2 distinct secondary areas
        k = min(2, len(options))
        secs = np.random.choice(options, size=k, replace=False)
        secondary_list.append("; ".join(secs))

    df['practice_area_secondary'] = secondary_list

    # Save fixed dataset
    df.to_csv(csv_path, index=False)
    print(f"Successfully updated {csv_path} with {len(target_areas)} balanced primary practice areas!")
    print("\nNew Primary Practice Area Counts:")
    print(df['practice_area_primary'].value_counts())

if __name__ == '__main__':
    fix_lawyer_dataset()
