# MultiVCFAnalyzer

MultiVCFAnalyzer reads multiple VCF files as produced by the GATK UnifiedGenotyper and after filtering provides the combined genotype calls in a number of formats that are suitable for follow-up analyses such as phylogenetic reconstruction, SNP effect analyses, population genetic analyses etc.<br>
Furthermore, the results are provided in the form of various tables for manual inspection and presentation/publication purposes.

If you use MultiVCFAnalyzer please cite:

Bos, Harkins, Herbig, Coscolla et al.<br>
Pre-Columbian mycobacterial genomes reveal seals as a source of New World human tuberculosis<br>
Nature 514, 494–497 (23 October 2014) doi:10.1038/nature13591

In case of any questions please contact Alexander Herbig (herbig@shh.mpg.de).

Please start the program with the following parameters in exactly this order:

SNP effect analysis result file (from SnpEff; txt format)<br>
Reference genome fasta file<br>
Reference genome gene annotation (gff)<br>
Output directory<br>
Write allele frequencies ('T' or 'F')<br>
Minimal genotyping quality (GATK)<br>
Minimal coverage for base call<br>
Minimal allele frequency for homozygous call<br>
Minimal allele frequency for heterozygous call<br>
List of positions to exclude (gff)<br>
[vcf_files ...] input vcf files as generated by the GATK UnifiedGenotyper<br>

To omit an optional input file put NA as the file name.<br>
The SnpEff file, the reference gene annotation, and excluded positions are optional.
