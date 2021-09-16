import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;



/**
 * 
 * @author Alexander Herbig
 *
 */
public class MultiVCFAnalyzer {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception
	{
		String programName = "MultiVCFAnalyzer";
		String author = "Alexander Herbig";
		String authorsEmail = "herbig@shh.mpg.de";
		String version = "0.85.2";
		
		System.out.println(programName+" - "+version+"\nby "+author+"\n");
		
		String helpString = "Please start the program with the following parameters in exactly this order:\n\nSNP effect analysis result file (from SnpEff; txt format)\nReference genome fasta file\nReference genome gene annotation (gff)\nOutput directory\nWrite allele frequencies ('T' or 'F')\nMinimal genotyping quality (GATK)\nMinimal coverage for base call\nMinimal allele frequency for homozygous call\nMinimal allele frequency for heterozygous call\nList of positions to exclude (gff)\n[vcf_files ...] input vcf files as generated by the GATK UnifiedGenotyper\n\nTo omit an optional input file put NA as the file name.\nThe SnpEff file, the reference gene annotation, and excluded positions are optional.";
		
		if(args.length==0 || args[0].equalsIgnoreCase("--help") || args[0].equalsIgnoreCase("-help") || args[0].equalsIgnoreCase("-?") || args[0].equalsIgnoreCase("-h"))
		{
			System.out.println("--- How to use "+programName+" ---\n");
			System.out.println(helpString);
			System.out.println("\nIn case of any questions contact "+author+" ("+authorsEmail+").");
			return;
		}
		
		String snpEffOutputFile = args[0];
		
		String refFastaFile = args[1]; //
		
		String refGFF = args[2];
		
		String outputFolder = args[3];
		
		String outSNPfasta = outputFolder+"/snpAlignment.fasta";
		String outSNPfastaWithRef = outputFolder+"/snpAlignmentIncludingRefGenome.fasta";
		String outFullAlignmentFasta = outputFolder+"/fullAlignment.fasta";
		String outStatTable = outputFolder+"/snpStatistics.tsv";
		String outSNPtable = outputFolder+"/snpTable.tsv";
		String outSNPtableWithUncertaintyCalls = outputFolder+"/snpTableWithUncertaintyCalls.tsv";
		String outSNPtable4SnpEff = outputFolder+"/snpTableForSnpEff.tsv";
		String outSNPtableWithSnpEffInfos = outputFolder+"/snpTableWithSnpEffInfos.tsv";
		String outSNPtableWithUncertaintyCallsWithSnpEffInfos = outputFolder+"/snpTableWithUncertaintyCallsWithSnpEffInfos.tsv";
		String outGenoTypeTable4Structure = outputFolder+"/structureGenotypes.tsv";
		String outGenoTypeTable4StructureCompDel = outputFolder+"/structureGenotypes_noMissingData-Columns.tsv";
		String outJSON = outputFolder+"/MultiVCFAnalyzer.json";
		
		String infoOut = outputFolder+"/info.txt";
		
		boolean writeFreqsInStatTable = parseBoolean(args[4]);
		
		double minQual = Double.parseDouble(args[5]);
		int minCov = Integer.parseInt(args[6]);
		double minHomSNPallelFreq = Double.parseDouble(args[7]);
		double minHetSNPallelFreq = Double.parseDouble(args[8]);
		
		String positions2ExcludeFiles = args[9];//Can be upt to two files: 1st: repeat regs etc. 2nd: CDS file to exclude 1st,2nd codon pos
		
		Set<Integer> positions2ExcludeSet = getPositionsToExclude(positions2ExcludeFiles);
		
		int vcfArgumentsOffset = 10;
		
		int numVCFs = args.length-vcfArgumentsOffset;
		
		String refGenome = FASTAParser.parseDNA(refFastaFile).values().iterator().next();
		
		String refGenomeName = FASTAParser.parseDNA(refFastaFile).keySet().iterator().next();
		
		int numOutgroups = 0;
		
		//write initial infos
		BufferedWriter infobw = new BufferedWriter(new FileWriter(infoOut));
		
		infobw.write(programName+" - "+version+"\nby "+author+"\n\n");
		infobw.write("Input files:\n");
		infobw.write("Reference genome fasta file: "+refFastaFile+"\n");
		infobw.write("Reference genome gene annotation (gff): "+refGFF+"\n");
		infobw.write("List of positions to exclude (gff): "+positions2ExcludeFiles+"\n");
		infobw.write("SNP effect analysis result file (from SnpEff): "+snpEffOutputFile+"\n");
		
		infobw.write("\nOutput files:\n");
		infobw.write("Output directory: "+outputFolder+"\n");
		infobw.write("SNP alignment (fasta): "+outSNPfasta+"\n");
		infobw.write("SNP alignment including entry for reference genome (fasta): "+outSNPfastaWithRef+"\n");
		infobw.write("SNP table: "+outSNPtable+"\n");
		infobw.write("SNP table with uncertainty calls: "+outSNPtableWithUncertaintyCalls+"\n");
		if((new File(snpEffOutputFile)).exists())
		{
			infobw.write("SNP table with SnpEff infos: "+outSNPtableWithSnpEffInfos+"\n");
			infobw.write("SNP table with uncertainty calls with SnpEff infos: "+outSNPtableWithUncertaintyCallsWithSnpEffInfos+"\n");
		}
		infobw.write("SNP table to be used as input for SnpEff: "+outSNPtable4SnpEff+"\n");
		infobw.write("SNP table to be used as input for STRUCTURE: "+outGenoTypeTable4Structure+"\n");
		infobw.write("SNP table to be used as input for STRUCTURE (no columns with missing data): "+outGenoTypeTable4StructureCompDel+"\n");
		infobw.write("SNP calling statistics: "+outStatTable+"\n");
		
		infobw.write("\nParameters:\n");
		infobw.write("Minimal genotyping quality (GATK): "+minQual+"\n");
		infobw.write("Minimal coverage for base call: "+minCov+"\n");
		infobw.write("Minimal allele frequency for homozygous call: "+minHomSNPallelFreq+"\n");
		infobw.write("Minimal allele frequency for heterozygous call: "+minHetSNPallelFreq+"\n");
		infobw.write("Write allele frequencies: "+writeFreqsInStatTable+"\n");
		
		infobw.write("\nAdditional notes:\n");
		infobw.write("Reference genome name: "+refGenomeName+"\n");
		infobw.write("Number of genomes (vcf files): "+numVCFs+"\n");
		Date date = new Date();
		infobw.write("Run started: "+date.toGMTString()+"\n");
		


		// make output folder
		File dataDir = new File(outputFolder);
		if(!dataDir.exists())
		{
			dataDir.mkdir();
			System.out.println("Created output directory:\n"+outputFolder);
			infobw.write("Output directory did not exist and was created.\n");
		}
		
		//SNP array
		char[][] snpColumns = new char[refGenome.length()][numVCFs];
		char[][] uncertainSnpColumns = new char[refGenome.length()][numVCFs];
		
		Set<Integer> snpPositions = new HashSet<Integer>();
		
		boolean[] missingDataPos = new boolean[refGenome.length()];
		
		//Freq array
		double[][] snpFrequencies = null;
		if(writeFreqsInStatTable)
		{
			snpFrequencies = new double[refGenome.length()][numVCFs];
		}
		
		//Fill SNP array
		for(int i=0; i<snpColumns.length; i++)
			for(int j=0; j<numVCFs; j++)
			{
				snpColumns[i][j] = refGenome.charAt(i);
				uncertainSnpColumns[i][j] = refGenome.charAt(i);
			}
		
		
		/////////////////////////
		// BEGIN -- Parse VCFs //
		/////////////////////////
		BufferedReader br;
		BufferedWriter statbw = new BufferedWriter(new FileWriter(outStatTable));
		statbw.write("SNP statistics for "+numVCFs+" samples.\nQuality Threshold: "+minQual+"\nCoverage Threshold: "+minCov+"\nMinimum SNP allele frequency: "+minHomSNPallelFreq+"\n");
		statbw.write("sample\tSNP Calls (all)\tSNP Calls (het)\tcoverage(fold)\tcoverage(percent)\trefCall\tallPos\tnoCall\tdiscardedRefCall\tdiscardedVarCall\tfilteredVarCall\tunhandledGenotype\n");
		
		//FileWriter for JSON output
		FileWriter jsonfw = new FileWriter(outJSON);
		//Add all the metadata to JSON output too
		HashMap<String, Object> json_map = new HashMap<>();
		HashMap<String, Object> meta_map = new HashMap<>();
		meta_map.put("numVCFs", numVCFs);
		meta_map.put("quality_threshold", minQual);
		meta_map.put("coverage_threshold", minCov);
		meta_map.put("min_snp_allele_freq", minHomSNPallelFreq);
		meta_map.put("tool_name", "MultiVCFAnalyzer");
		meta_map.put("version", version);
		json_map.put("metadata", meta_map);

		HashMap<String, Object> metric_map = new HashMap<>();

		char nChar='N';
		char rChar='R';
		//counter
		int covCount = 0;
		int allPos = 0;
		int noCallPos = 0;
		int nonStandardRefChars = 0;
		int refCallPos = 0;
		int varCallPos = 0;
		int hetVarCallPos = 0;
		int discardedRefCall = 0;
		int discardedVarCall = 0;
		int filteredVarCall = 0;
		int unknownCall = 0;
		
		int ns;
		double nperc;
		double covround;
		
		String line;
		String[] cols;
		
		double qual;
		int cov;
		double SNPallelFreq;
		String[] allelCols;
		
		int lastPos1based = 0;
		int currPos1based = 0;
		long startTime = System.currentTimeMillis();
		long timeLeft;
		boolean outgroup;
		for(int vcfIndex =0; vcfIndex<numVCFs; vcfIndex++)
		{
			br = new BufferedReader(new FileReader(args[vcfIndex+vcfArgumentsOffset]));
			HashMap<String,Object> sample_map = new HashMap<>();
			
			System.out.println("Now processing "+(vcfIndex+1)+"/"+numVCFs+": "+getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset]));
			
			//Is this an outgroup?
			//If yes, do not consider positions that are only variant in the outgroup.
			if(getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset]).startsWith("outgroup"))
			{
				System.out.println("This is an outgroup! Outgroup-specific SNPs will not be considered.");
				infobw.write(getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset])+" was labeled as an outgroup.\n");
				outgroup=true;
				numOutgroups++;
			}
			else
				outgroup=false;
			
			//counter
			covCount=0;
			allPos = 0;
			noCallPos = 0;
			nonStandardRefChars = 0;
			refCallPos = 0;
			varCallPos = 0;
			hetVarCallPos = 0;
			discardedRefCall = 0;
			discardedVarCall = 0;
			filteredVarCall = 0;
			unknownCall = 0;
			
			lastPos1based = 0;
			currPos1based = 0;
			
			while((line=br.readLine()) != null)
			{
				if(line.startsWith("#"))
				{
					continue;
				}
				
				//count
				allPos++;
				
				if(allPos%500000==0)
					System.out.println(allPos+" positions processed.");
				
				cols = line.split("\t");
				
				//insert Ns at not handled sites, which are left out by GATK
				lastPos1based=currPos1based;
			
				currPos1based = Integer.parseInt(cols[0]);
				
				
				if(currPos1based-lastPos1based!=1)
				{
					if(lastPos1based>=currPos1based)
						throw new Error("ERROR: Base calls in the vcf file are not sorted! (Note that multiple chromosomes are not supported.)");
					
					for(int i=lastPos1based+1; i<currPos1based; i++)
					{
						allPos++;
						nonStandardRefChars++;
						
						snpColumns[currPos1based-1][vcfIndex] = nChar;
						uncertainSnpColumns[currPos1based-1][vcfIndex] = nChar;
						missingDataPos[currPos1based-1] = true;
						
					}
				}
				
				allelCols = cols[9].split(":");
				
				//No call
				if(allelCols[0].equals("./."))
				{
					noCallPos++;
					
					snpColumns[currPos1based-1][vcfIndex] = nChar;
					uncertainSnpColumns[currPos1based-1][vcfIndex] = nChar;
					missingDataPos[currPos1based-1] = true;
					
				}
				//Reference Call
				else if(allelCols[0].equals("0/0"))
				{
					qual = Double.parseDouble(cols[5]);
					cov=Integer.parseInt(allelCols[1]);
					covCount+=cov;
					
					if(qual>=minQual && cov >= minCov)
					{
						refCallPos++;
						
						// do nothing since reference is called
					}
					else
					{
						discardedRefCall++;
						
						snpColumns[currPos1based-1][vcfIndex] = nChar;
						uncertainSnpColumns[currPos1based-1][vcfIndex] = rChar;
						missingDataPos[currPos1based-1] = true;
						
					}
				}
				//variant call
				else if(allelCols[0].equals("0/1") || allelCols[0].equals("1/1"))
				{
					qual = Double.parseDouble(cols[5]);
					cov=Integer.parseInt(allelCols[1].split(",")[1]);
					SNPallelFreq=Math.min((double)cov/(cov+Integer.parseInt(allelCols[1].split(",")[0])-1) , 1); // -1 because once doesn't count
					
					if(writeFreqsInStatTable)
						snpFrequencies[currPos1based-1][vcfIndex] = SNPallelFreq;
					
					covCount+=cov+Integer.parseInt(allelCols[1].split(",")[0]);
					
					if(qual>=minQual && cov >= minCov && SNPallelFreq >= minHomSNPallelFreq)
					{
						if(positions2ExcludeSet.contains(currPos1based))
							filteredVarCall++;
						else
							varCallPos++;
						
						snpColumns[currPos1based-1][vcfIndex] = cols[4].charAt(0);
						uncertainSnpColumns[currPos1based-1][vcfIndex] = cols[4].charAt(0);
						
						if(!outgroup)
							snpPositions.add(currPos1based);
						
					}
					else if(qual>=minQual && cov >= minCov && SNPallelFreq >= minHetSNPallelFreq)
					{
						if(positions2ExcludeSet.contains(currPos1based))
							filteredVarCall++;
						else
						{
							varCallPos++;
							hetVarCallPos++;
						}
						
						snpColumns[currPos1based-1][vcfIndex] = getAmbiguousBase(snpColumns[currPos1based-1][vcfIndex] , cols[4].charAt(0));
						uncertainSnpColumns[currPos1based-1][vcfIndex] = getAmbiguousBase(snpColumns[currPos1based-1][vcfIndex] , cols[4].charAt(0));

						if(!outgroup)
							snpPositions.add(currPos1based);
						
					}
					else
					{
						cov=Integer.parseInt(allelCols[1].split(",")[0]);
						
						SNPallelFreq=(double)cov/(cov+Integer.parseInt(allelCols[1].split(",")[1])-1); // -1 because once doesn't count
						
						if(qual>=minQual && cov >= minCov)
						{
							if(SNPallelFreq>=minHomSNPallelFreq)
							{
								refCallPos++;
							}
							else
							{
								//System.err.println("DEBUG: "+getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset])+" pos: "+currPos1based);
								discardedVarCall++;
								
								snpColumns[currPos1based-1][vcfIndex] = nChar;
								uncertainSnpColumns[currPos1based-1][vcfIndex] = nChar;

								missingDataPos[currPos1based-1] = true;
							}
							// do nothing since reference is called
						}
						else
						{
							discardedVarCall++;
							
							snpColumns[currPos1based-1][vcfIndex] = nChar;
							
							if(allelCols[0].equals("1/1"))
								uncertainSnpColumns[currPos1based-1][vcfIndex] = Character.toLowerCase(cols[4].charAt(0));
							else
								uncertainSnpColumns[currPos1based-1][vcfIndex] = nChar;

							missingDataPos[currPos1based-1] = true;
							
						}
					}
					
				}
				//unhandled genotype
				else
				{
					unknownCall++;
					//System.err.println("WARNING: The Genotype "+allelCols[0]+" cannot be handled:\n"+line+"\nInserting 'N'!");
					
					snpColumns[currPos1based-1][vcfIndex] = nChar;
					uncertainSnpColumns[currPos1based-1][vcfIndex] = nChar;
					missingDataPos[currPos1based-1] = true;
				}
				
			}
			br.close();
			
			//write stats
			ns = discardedRefCall+discardedVarCall+noCallPos+unknownCall+nonStandardRefChars;
			nperc = Math.round(((ns*100d)/allPos)*100)/100d;
			covround = Math.round((covCount/(double)allPos)*100)/100d;

			statbw.write(getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset])+"\t"+varCallPos+"\t"+hetVarCallPos+"\t"+covround+"\t"+(100-nperc)+"\t"+refCallPos+"\t"+allPos+"\t"+noCallPos+"\t"+discardedRefCall+"\t"+discardedVarCall+"\t"+filteredVarCall+"\t"+unknownCall+"\n");

			//Write the same to JSON dictionary
			sample_map.put("SNP Calls (all)",varCallPos );
			sample_map.put("SNP Calls (het)", hetVarCallPos);
			sample_map.put("coverage (fold)", covround);
			sample_map.put("coverage (percent)", (100-nperc));
			sample_map.put("refCall",refCallPos);
			sample_map.put("allPos", allPos);
			sample_map.put("noCall", noCallPos);
			sample_map.put("discardedRefCall", discardedRefCall);
			sample_map.put("discardedVarCall", discardedVarCall);
			sample_map.put("filteredVarCall", filteredVarCall);
			sample_map.put("unhandledGenotype", unknownCall);
			//Put that back to metric_map for each sample
			metric_map.put(getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset]), sample_map);
	
			//Time
			timeLeft =Math.round(((System.currentTimeMillis()-startTime)/(double)(vcfIndex+1))*(numVCFs-(vcfIndex+1)));
			System.out.println("\t("+Math.round(timeLeft/60000)+" minutes remaining)");
		}
		statbw.close();

		//Write out the proper JSON file
		json_map.put("metrics", metric_map);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();		

		String json = gson.toJson(json_map);
		jsonfw.write(json);
		jsonfw.flush();
        jsonfw.close();
		//////////////////////
		//END -- Parse VCFs //
		//////////////////////
		
		// Check if SNP positions are really SNPs
		
		// Exclude Positions (like repeat regions)
		
		excludePositionsFromSet(snpPositions, positions2ExcludeFiles);
		
		
		//create list
		List<Integer> snpPositionList = new LinkedList<Integer>(snpPositions);
		Collections.sort(snpPositionList);
		
		//Write SNP table
		System.out.println("Writing SNP table:\n"+outSNPtable);
		
		BufferedWriter snptabbw = new BufferedWriter(new FileWriter(outSNPtable));
		
		snptabbw.write("Position\tRef");
		for(int vcfIndex=0; vcfIndex<numVCFs; vcfIndex++)
		{
			snptabbw.write("\t"+getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset]));
		}
		snptabbw.newLine();
		
		char tmpchar;
		for(int pos : snpPositionList)
		{
			snptabbw.write(pos+"\t"+refGenome.charAt(pos-1));
			
			for(int vcfIndex=0; vcfIndex<numVCFs; vcfIndex++)
			{
				snptabbw.write("\t");
				tmpchar = snpColumns[pos-1][vcfIndex];
				if(tmpchar==refGenome.charAt(pos-1))
					snptabbw.write(".");
				else
				{
					snptabbw.write(tmpchar);
					
					if(writeFreqsInStatTable && tmpchar!='N')
					{
						snptabbw.write(" ("+Math.round(snpFrequencies[pos-1][vcfIndex]*1000d)/10d+")");
					}
				}
			}
			snptabbw.newLine();
		}
		
		snptabbw.close();
		
		//Write SNP table with uncertainty calls
		//Identical to previous code (3 differences)
		System.out.println("Writing SNP table with uncertainty calls:\n"+outSNPtableWithUncertaintyCalls); // 1/3 differences
		
		snptabbw = new BufferedWriter(new FileWriter(outSNPtableWithUncertaintyCalls)); // 2/3 differences
		
		snptabbw.write("Position\tRef");
		for(int vcfIndex=0; vcfIndex<numVCFs; vcfIndex++)
		{
			snptabbw.write("\t"+getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset]));
		}
		snptabbw.newLine();
		
		for(int pos : snpPositionList)
		{
			snptabbw.write(pos+"\t"+refGenome.charAt(pos-1));
			
			for(int vcfIndex=0; vcfIndex<numVCFs; vcfIndex++)
			{
				snptabbw.write("\t");
				tmpchar = uncertainSnpColumns[pos-1][vcfIndex]; // 3/3 differences
				if(tmpchar==refGenome.charAt(pos-1))
					snptabbw.write(".");
				else
				{
					snptabbw.write(tmpchar);
					
					if(writeFreqsInStatTable && tmpchar!='N')
					{
						snptabbw.write(" ("+Math.round(snpFrequencies[pos-1][vcfIndex]*1000d)/10d+")");
					}
				}
			}
			snptabbw.newLine();
		}
		
		snptabbw.close();
		
		//Write SNP multi fasta
		BufferedWriter bw = new BufferedWriter(new FileWriter(outSNPfasta));
		
		System.out.println("Writing SNP alignment (fasta):\n"+outSNPfasta);
		
		StringBuffer tmpSeq;
		for(int vcfIndex=0; vcfIndex<numVCFs; vcfIndex++)
		{
			tmpSeq=new StringBuffer();
			for(int pos : snpPositionList)
				tmpSeq.append(snpColumns[pos-1][vcfIndex]);
			
			FASTAWriter.write(bw, getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset]), tmpSeq.toString());
		}
		
		bw.close();
		
		//Write SNP multi fasta with reference genome 
		
		bw = new BufferedWriter(new FileWriter(outSNPfastaWithRef)); 
		
		System.out.println("Writing SNP alignment including reference genome (fasta):\n"+outSNPfastaWithRef);
		
		tmpSeq=new StringBuffer();
		for(int pos : snpPositionList)
			tmpSeq.append(refGenome.charAt(pos-1));
		
		FASTAWriter.write(bw, "Reference_"+refGenomeName, tmpSeq.toString());
		
		for(int vcfIndex=0; vcfIndex<numVCFs; vcfIndex++)
		{
			tmpSeq=new StringBuffer();
			for(int pos : snpPositionList)
				tmpSeq.append(snpColumns[pos-1][vcfIndex]);
			
			FASTAWriter.write(bw, getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset]), tmpSeq.toString());
		}
				
		bw.close();
		
		//Write FULL multi fasta with reference genome 
		
		bw = new BufferedWriter(new FileWriter(outFullAlignmentFasta)); 
		
		System.out.println("Writing full alignment including reference genome (fasta):\n"+outFullAlignmentFasta);
		
		tmpSeq=new StringBuffer();
		for(int pos=1;pos<=refGenome.length();pos++)
			tmpSeq.append(refGenome.charAt(pos-1));
		
		FASTAWriter.write(bw, "Reference_"+refGenomeName, tmpSeq.toString());
		
		for(int vcfIndex=0; vcfIndex<numVCFs; vcfIndex++)
		{
			tmpSeq=new StringBuffer();
			for(int pos=1;pos<=refGenome.length();pos++)
				tmpSeq.append(snpColumns[pos-1][vcfIndex]);
			
			FASTAWriter.write(bw, getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset]), tmpSeq.toString());
		}
				
		bw.close();
		
		//write Genotypes for Structure
		bw = new BufferedWriter(new FileWriter(outGenoTypeTable4Structure));
		
		System.out.println("Writing genotypes for structure analysis:\n"+outGenoTypeTable4Structure);
		
		//first row for linked loci
		bw.append("-1");
		int prevpos=0;
		boolean first = true;
		for(int pos : snpPositionList)
		{
			if(first)
			{
				first=false;
				prevpos = pos;
				continue;
			}
			else
			{
				bw.append("\t"+(pos-prevpos));
				prevpos=pos;
			}
		}
		
		for(int vcfIndex=0; vcfIndex<numVCFs; vcfIndex++)
		{
			bw.newLine();
			bw.append(getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset]));
			for(int pos : snpPositionList)
				bw.append("\t"+getGenotypeEncoding(snpColumns[pos-1][vcfIndex]));
		}
		
		bw.close();
		
		//write Genotypes for Structure (no missing data)
		bw = new BufferedWriter(new FileWriter(outGenoTypeTable4StructureCompDel));
		
		System.out.println("Writing genotypes for structure analysis (no missing data columns):\n"+outGenoTypeTable4StructureCompDel);
		
		//first row for linked loci
		bw.append("-1");
		prevpos = 0;
		first = true;
		for(int pos : snpPositionList)
		{
			if(!missingDataPos[pos-1])
			{
				if(first)
				{
					first=false;
					prevpos = pos;
					continue;
				}
				else
				{
					bw.append("\t"+(pos-prevpos));
					prevpos=pos;
				}
			}
		}
		
		for(int vcfIndex=0; vcfIndex<numVCFs; vcfIndex++)
		{
			bw.newLine();
			bw.append(getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset]));
			for(int pos : snpPositionList)
				if(!missingDataPos[pos-1])
					bw.append("\t"+getGenotypeEncoding(snpColumns[pos-1][vcfIndex]));
		}
		
		bw.close();

		//Write SNPeff table
		bw = new BufferedWriter(new FileWriter(outSNPtable4SnpEff));
		
		Set<Character> validChars = new HashSet<Character>();
		validChars.add('A'); validChars.add('C'); validChars.add('G'); validChars.add('T');
		
		Set<Character> toChars = new HashSet<Character>();
		char[] columnChars;
		
		first=true;
		
		for(int pos : snpPositionList)
		{
			toChars.clear();
			columnChars = snpColumns[pos-1];
			
			for(char c : columnChars)
			{
				if(validChars.contains(c) && refGenome.charAt(pos-1)!=c)
					toChars.add(c);
			}
			
			for(char c : toChars)
			{
				if(!first)
					bw.newLine();
				else
					first=false;
				
				bw.append(refGenomeName+"\t"+pos+"\t"+refGenome.charAt(pos-1)+"\t"+c);
			}
		}
		
		bw.close();
		
		
		//Write SNP table with SnpEffInfos
		if((new File(snpEffOutputFile)).exists())
		{
			System.out.println("Writing SNP table:\n"+outSNPtableWithSnpEffInfos);
			
			
			//SNPeff SNPs
			List<SNPeffSNP> esnps = Read.readSNPeffSNPs(snpEffOutputFile);
			
			Map<Integer,List<SNPeffSNP>> esnpsmap = new HashMap<Integer, List<SNPeffSNP>>();
			
			for(SNPeffSNP esnp : esnps)
			{
				if(!esnpsmap.containsKey(esnp.getPos()))
				{
					esnpsmap.put(esnp.getPos(), new LinkedList<SNPeffSNP>());
				}
				
				esnpsmap.get(esnp.getPos()).add(esnp);
			}
			
			//Proteins
			Map<String,Gene> geneMap = new HashMap<String, Gene>();
			
			for(Gene g : Read.parseGFF(refGFF))
				geneMap.put(g.name, g);
			
			//Write
			
			snptabbw = new BufferedWriter(new FileWriter(outSNPtableWithSnpEffInfos));
			
			snptabbw.write("Position\tRef\tSNP");
			for(int vcfIndex=0; vcfIndex<numVCFs; vcfIndex++)
			{
				snptabbw.write("\t"+getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset]));
			}
			snptabbw.write("\tSNP Effect\tGene ID\tGene name\tGene function\told_AA/new_AA\tOld_codon/New_codon\tCodon_Num(CDS)\tCDS_size");
			snptabbw.newLine();
			
			//char tmpchar;
			Gene g;
			String anno;
			String length;
			for(int pos : snpPositionList)
			{
				for(SNPeffSNP es : esnpsmap.get(pos))
				{
					g = geneMap.get(es.geneID);
					
					//anno/length
					if(g!=null)
					{
						anno = g.anno;
						length = Integer.toString(g.length());
					}
					else
					{
						anno = "";
						length = "";
					}
					
					
					
					snptabbw.write(pos+"\t"+refGenome.charAt(pos-1)+"\t"+es.to);
					
					for(int vcfIndex=0; vcfIndex<numVCFs; vcfIndex++)
					{
						snptabbw.write("\t");
						tmpchar = snpColumns[pos-1][vcfIndex];
						if(tmpchar==refGenome.charAt(pos-1))
							snptabbw.write(".");
						else
						{
							snptabbw.write(tmpchar);
							
							if(writeFreqsInStatTable && tmpchar!='N')
							{
								snptabbw.write(" ("+Math.round(snpFrequencies[pos-1][vcfIndex]*1000d)/10d+")");
							}
						}
					}
					
					snptabbw.write("\t"+es.effect+"\t"+es.geneID+"\t"+es.geneName+"\t"+anno+"\t"+es.aaChange+"\t"+es.codonChange+"\t"+es.codonNum+"\t"+length);
					
					snptabbw.newLine();
				}
			}
			
			snptabbw.close();
		}
		
		//Write SNP table with SnpEffInfos with uncertainty calls
		//Identical to previous code (3 differences)
		if((new File(snpEffOutputFile)).exists())
		{
			System.out.println("Writing SNP table:\n"+outSNPtableWithUncertaintyCallsWithSnpEffInfos); // 1/3 differences
			
			
			//SNPeff SNPs
			List<SNPeffSNP> esnps = Read.readSNPeffSNPs(snpEffOutputFile);
			
			Map<Integer,List<SNPeffSNP>> esnpsmap = new HashMap<Integer, List<SNPeffSNP>>();
			
			for(SNPeffSNP esnp : esnps)
			{
				if(!esnpsmap.containsKey(esnp.getPos()))
				{
					esnpsmap.put(esnp.getPos(), new LinkedList<SNPeffSNP>());
				}
				
				esnpsmap.get(esnp.getPos()).add(esnp);
			}
			
			//Proteins
			Map<String,Gene> geneMap = new HashMap<String, Gene>();
			
			for(Gene g : Read.parseGFF(refGFF))
				geneMap.put(g.name, g);
			
			//Write
			
			snptabbw = new BufferedWriter(new FileWriter(outSNPtableWithUncertaintyCallsWithSnpEffInfos)); // 2/3 differences
			
			snptabbw.write("Position\tRef\tSNP");
			for(int vcfIndex=0; vcfIndex<numVCFs; vcfIndex++)
			{
				snptabbw.write("\t"+getSampleNameFromPath(args[vcfIndex+vcfArgumentsOffset]));
			}
			snptabbw.write("\tSNP Effect\tGene ID\tGene name\tGene function\told_AA/new_AA\tOld_codon/New_codon\tCodon_Num(CDS)\tCDS_size");
			snptabbw.newLine();
			
			//char tmpchar;
			Gene g;
			String anno;
			String length;
			for(int pos : snpPositionList)
			{
				for(SNPeffSNP es : esnpsmap.get(pos))
				{
					g = geneMap.get(es.geneID);
					
					//anno/length
					if(g!=null)
					{
						anno = g.anno;
						length = Integer.toString(g.length());
					}
					else
					{
						anno = "";
						length = "";
					}
					
					
					
					snptabbw.write(pos+"\t"+refGenome.charAt(pos-1)+"\t"+es.to);
					
					for(int vcfIndex=0; vcfIndex<numVCFs; vcfIndex++)
					{
						snptabbw.write("\t");
						tmpchar = uncertainSnpColumns[pos-1][vcfIndex]; // 3/3 differences
						if(tmpchar==refGenome.charAt(pos-1))
							snptabbw.write(".");
						else
						{
							snptabbw.write(tmpchar);
							
							if(writeFreqsInStatTable && tmpchar!='N')
							{
								snptabbw.write(" ("+Math.round(snpFrequencies[pos-1][vcfIndex]*1000d)/10d+")");
							}
						}
					}
					
					snptabbw.write("\t"+es.effect+"\t"+es.geneID+"\t"+es.geneName+"\t"+anno+"\t"+es.aaChange+"\t"+es.codonChange+"\t"+es.codonNum+"\t"+length);
					
					snptabbw.newLine();
				}
			}
			
			snptabbw.close();
		}
		
		date = new Date();
		infobw.write("Run finished: "+date.toGMTString()+"\n");
		
		infobw.write("\nList of VCF files:\n");
		for(int vcfIndex =0; vcfIndex<numVCFs; vcfIndex++)
		{
			infobw.write(args[vcfIndex+vcfArgumentsOffset]+"\n");
		}
		
		infobw.close();
		
		//Messages about outgroup
		if(numOutgroups==0)
			System.out.println("No outgroup has been defined. All samples will be treated equally.");
		if(numOutgroups==1)
			System.out.println("One sample has been labeled as outgroup.\nSNPs only called in this sample were not considered in the SNP alignment or SNP tables.\nBut they were counted in the statistics table.");
		if(numOutgroups>1)
			System.out.println("WARNING: Multiple samples have been labeled as outgroup!\nSNPs only called in these samples were not considered in the SNP alignment or SNP tables.\nBut they were counted in the statistics table.");
		System.out.println("");
		
		System.out.println("All done! ("+Math.round((System.currentTimeMillis()-startTime)/60000d)+" minutes)");
	}
	
	private static void excludePositionsFromSet(Set<Integer> positions, String posToExcludeFiles) throws Exception
	{
		String[] filenames = posToExcludeFiles.split(",");
		
		File excludefile = new File(filenames[0]);
		
		System.out.println("Excluding positions...");
		
		int count =0;
		BufferedReader br;
		
		//exclude regions (like rep regions, certain genes, etc)
		if(excludefile.exists())
		{
			
			String[] cells;
			int start;
			int end;
			
			br = new BufferedReader(new FileReader(excludefile));
			for(String line=br.readLine(); line!=null;line=br.readLine())
			{
				line = line.trim();
				if(line.length()==0)
					continue;
				if(line.startsWith("#"))
					continue;
				
				cells = line.split("[\\t]");
				
				// start = 3
				start = Integer.parseInt(cells[3]);
				
				// end = 4
				end = Integer.parseInt(cells[4]);
				
				for(int i=start;i<=end;i++)
				{
					if(positions.contains(i))
					{
						count++;
						positions.remove(i);
					}
				}
			}
			
			br.close();
		
		}
		
		//exclude 1st and 2nd codon position
		if(filenames.length>1)
		{
			excludefile = new File(filenames[1]);
			
			if(excludefile.exists())
			{
				List<Gene> cdss = Read.getCDSfromGFF(excludefile.getAbsolutePath());
				
				for(Gene g : cdss)
				{
					if(g.strand=='+')
					{
						for(int posGenome=g.start, posGene=1; posGenome<=g.end; posGenome++,posGene++)
						{
							if(posGene%3!=0)
							{
								count++;
								positions.remove(posGenome);
							}
						}
					}
					else if(g.strand=='-')
					{
						for(int posGenome=g.end, posGene=1; posGenome>=g.start; posGenome--,posGene++)
						{
							if(posGene%3!=0)
								positions.remove(posGenome);
						}
					}
				}
			}
		}
		
		
		
		System.out.println(count+" SNP positions excluded.");
	}
	
	private static Set<Integer> getPositionsToExclude(String posToExcludeFile) throws Exception
	{
		File infile = new File(posToExcludeFile);
		
		Set<Integer> res = new HashSet<Integer>();
		
		if(!infile.exists())
		{
			System.err.println("No positions to exclude provided! All positions will be used!");
			return res;
		}
		
		
		
		String[] cells;
		int start;
		int end;
		
		BufferedReader br = new BufferedReader(new FileReader(infile));
		for(String line=br.readLine(); line!=null;line=br.readLine())
		{
			line = line.trim();
			if(line.length()==0)
				continue;
			if(line.startsWith("#"))
				continue;
			
			cells = line.split("[\\t]");
			
			// start = 3
			start = Integer.parseInt(cells[3]);
			
			// end = 4
			end = Integer.parseInt(cells[4]);
			
			for(int i=start;i<=end;i++)
			{
				res.add(i);
			}
		}
		
		br.close();
		
		return res;
	}
	
	public static boolean parseBoolean(String boo)
	{
		boolean res = false;
		if(boo.equalsIgnoreCase("true") || boo.equalsIgnoreCase("T") || boo.equalsIgnoreCase("1"))
			res=true;
		return res;
	}
	
	public static char getAmbiguousBase(char c1, char c2)
	{
		Set<Character> chars = new HashSet<Character>();
		chars.add(c1);
		chars.add(c2);
		
		if(c1==c2)
			return c1;
		
		if(chars.contains('G') && chars.contains('A'))
			return 'R';
		if(chars.contains('T') && chars.contains('C'))
			return 'Y';
		if(chars.contains('G') && chars.contains('T'))
			return 'K';
		if(chars.contains('A') && chars.contains('C'))
			return 'M';
		if(chars.contains('G') && chars.contains('C'))
			return 'S';
		if(chars.contains('A') && chars.contains('T'))
			return 'W';
		
		System.err.println("Illegal arguments in function getAmbiguousBase: "+c1+", "+c2);
		
		return 'N';
	}
	
	public static String getSampleNameFromPath(String sampleNameWithPath)
	{
		String[] res = sampleNameWithPath.split("/");
		
		if(res.length>=2)
			return res[res.length-2];
		else
			return sampleNameWithPath;
	}
	
	public static int getGenotypeEncoding(char nucleotide)
	{
		int res = -9;
		
		switch (nucleotide)
		{
			case 'A': res = 1;
			break;
			
			case 'C': res = 2;
			break;
			
			case 'G': res = 3;
			break;
			
			case 'T': res = 4;
			break;
			
			case 'N': res = -9;
			break;
			
			default: res = -9;
			System.err.println("Warning: No genotype encoding for invalid character '"+nucleotide+"'!\nEncoding is set to '-9' to indicate missing data.");
		
		}
		
		return res;
	}

}
