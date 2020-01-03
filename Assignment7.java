import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

/******************************************************
* *
* Name: Logan Zehm *
* Class: CS 3421 *
* Assignment: Assignment 7 Implementing a Data Cache Simulator
* Compile: "javac Assignment7.java" *
* Run: "java Assignment7 < trace.dat > trace.stats" *
* Comments: trace.dat and trace.stats are not static and can have different names *
* Only the current directory you are running the program is searched for trace.config *
* *
******************************************************/

public class Assignment7 {
	
	//information read from the trace.config file
	private static int numberOfSets;
	private static int setSize;
	private static int lineSize;
	
	//Stores all valid references read in from std in file
	private static ArrayList<Reference> references = new ArrayList<Reference>();
	//Stores all valid references parsed to block information such as tag, index, offset etc.
	private static ArrayList<Block> blocks = new ArrayList<Block>();
	//A 2d array to support multiple associativity levels that stores block objects
	private static Block[][] blockArray;
	//A hashmap and Arraylist to keep track of our least recently used index for each set
	private static HashMap<Integer, ArrayList<Integer>> LRU = new HashMap<>();
	
	//keep track of general information like hits and misses
	private static int totalHits = 0;
	private static int totalMisses = 0;
	private static int totalAccesses = 0;
	
	public static void main(String[] args) throws IOException {
		
		//read all the information from the trace file
		readTraceFile();
		//print the configuration information to std out file.
		printCacheConfiguration();
		
    	blockArray = new Block[numberOfSets][setSize];
    	
    	//initialize the LRU
    	for(int i = 0; i < numberOfSets; i++) {
    		LRU.put(i, new ArrayList<Integer>());
    	}
		
    	Scanner s = new Scanner(System.in);
    	String line;
    	int lineNumber = 1;
    	while(s.hasNextLine()) {
    		line = s.nextLine();
    		String[] values = line.split(":");
    		//if our parsed reference size is not 1,2,4,or 8 bytes its an illegal size and we need to throw a exception
    		if(Integer.parseInt(values[1]) == 1 || Integer.parseInt(values[1]) == 2 || Integer.parseInt(values[1]) == 4 || Integer.parseInt(values[1]) == 8) {
        		Reference ref = new Reference(values[0].charAt(0), Integer.parseInt(values[1]), Integer.parseInt(values[2],16));
        		Block block = new Block(values[2], lineSize, numberOfSets, Integer.parseInt(values[1]));
        		/* if offset is greater than the difference between the line size and the reference size
        		 * then the offset goes over the bound of the linesize meaning we need to throw a
        		 * misaligned reference exception.
        		 */
        		if(block.getOffset() <= (lineSize - Integer.parseInt(values[1]))) {
            		references.add(ref);
            		blocks.add(block);
        		} else {
        			System.err.println("line " + lineNumber + " has misaligned reference at address " + values[2] + " for size " + Integer.parseInt(values[1]));
        		}
    		} else {
    			System.err.println("line " + lineNumber + " has illegal size " + Integer.parseInt(values[1]));
    		}
    		lineNumber++;
    	}	
    	//store the total number of valid references read in.
    	totalAccesses = references.size();
    	//simulate the cache.
    	calculateCache();
    	//print all reference information
    	printReferenceInformation();
    	//calculate and print all hit, miss, and access statistics
    	printSimulationStatistics();
	}
	
	/*
	 * Calling this function reads in all information from the trace file.
	 * If the trace file is not found a file not found exception is thrown.
	 * The assumption is made in the assignment that the format in this config is valid.
	 */
	public static void readTraceFile() {
		File file = new File("trace.config");
		if(file.exists()) {
			Scanner s;
			try {
				s = new Scanner(file);
				String configInput;
				int counter = 0;
				while(s.hasNextLine() && counter < 3) {
					configInput = s.nextLine();
					//get just the number
					configInput = configInput.split(":")[1].trim();
					switch(counter) {
						case 0:
							numberOfSets = Integer.parseInt(configInput);
							break;
						case 1:
							setSize = Integer.parseInt(configInput);
							break;
						case 2:
							lineSize = Integer.parseInt(configInput);
							break;
					}
					counter++;
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		} else {
			System.out.println("Could not find trace.config in current directory.");
			System.exit(0);
		}
	}
	
	/*
	 * Calling this function actually loops through our 2d array of blocks
	 * and simulates the cache. In order to simulate the cache I loop through
	 * the list of blocks I already read in and implement them with the 2d array.
	 * At the same time I modify their values to simulate hits, valid bits, dirty bits, etc.
	 */
	public static void calculateCache() {
	    for(int i = 0; i < blocks.size(); i++) {
	        for(int j = 0; j < blockArray[blocks.get(i).getIndex()].length; j++) {
	        	if(blockArray[blocks.get(i).getIndex()][j] != null) {
	        		Block currentBlock = blockArray[blocks.get(i).getIndex()][j];
	        			if(blocks.get(i).getTag().equals(currentBlock.getTag())) {
			        		//hit
			        		blocks.get(i).setHit(true);
			        		blocks.get(i).setMemrefs(0);
			        		if(references.get(i).getAccessType().equals("write")) {
				        		blocks.get(i).markDirty();
			        		}
			        		
			        		LRU.get(blocks.get(i).getIndex()).remove(j);
			        		LRU.get(blocks.get(i).getIndex()).add(0, j);
			        		totalHits += 1;
			        		break;
			        	} else {
			        		if(j != blockArray[blocks.get(i).getIndex()].length - 1) continue;
			        		//miss but cache line is full
			        		blocks.get(i).setHit(false);
			        		if(currentBlock.getDirtyBit() == 1) {
			        			//should not need to flipDirty because its a new block.
			        			//blocks.get(i).flipDirtyBit();
				        		blocks.get(i).setMemrefs(2);
			        		} else {
				        		if(references.get(i).getAccessType().equals("write")) {
					        		blocks.get(i).markDirty();	
				        		}
				        		blocks.get(i).setMemrefs(1);
			        		}
			        		//move the lru index to the front of the list
			        		LRU.get(blocks.get(i).getIndex()).add(0, LRU.get(blocks.get(i).getIndex()).size() - 1);
			        		//replace the LRU index (which is the last entry in the list). Remove it from the end of the list
			        		//kind of an ugly line of code but its function is simple. I might modify it to make the parameters more clean in the future
			        		blockArray[blocks.get(i).getIndex()][LRU.get(blocks.get(i).getIndex()).remove(LRU.get(blocks.get(i).getIndex()).size()-1)] = blocks.get(i);
				        	totalMisses += 1;
			        		break;
			        	}
	        	} else {
	        		//miss but cache line still has space
	        		blocks.get(i).setHit(false);
	        		blocks.get(i).markValid();
	        		if(references.get(i).getAccessType().equals("write")) {
		        		blocks.get(i).markDirty();
	        		}
	        		blocks.get(i).setMemrefs(1);
	        		blockArray[blocks.get(i).getIndex()][j] = blocks.get(i);
	        		
	        		//add the index to the front of the lru
	        		LRU.get(blocks.get(i).getIndex()).add(0, j);
	        		totalMisses += 1;
	        		break;
	        	}
	        }
	    }
	}
	
	//This function prints the configuration information read from the trace file
	public static void printCacheConfiguration() {
		System.out.println("Cache Configuration");
		System.out.println();
		System.out.printf("   %d %d-way set associative entries\n", numberOfSets, setSize);
		System.out.printf("   of line size %d bytes", lineSize);
		System.out.println();
		System.out.println();
		System.out.println();
	}
	
	//This function prints the simulation statistics based on the total number of hits, misses, and accesses
	public static void printSimulationStatistics() {
		System.out.println();
		System.out.println();
		System.out.println("Simulation Summary Statistics");
		System.out.println("-----------------------------");
		System.out.printf("Total hits       : %d\n", totalHits);
		System.out.printf("Total misses     : %d\n", totalMisses);
		System.out.printf("Total accesses   : %d\n", totalAccesses);
		System.out.printf("Hit ratio        : %.6f\n", ((double) totalHits / (double) totalAccesses));
		System.out.printf("Miss ratio       : %.6f\n", ((double) totalMisses / (double) totalAccesses));
		System.out.println();
	}
	
	//This function loops through every valid reference that was read in and prints their simulation values
	public static void printReferenceInformation() {
		System.out.println("Results for Each Reference");
		System.out.println();
		System.out.println("Ref  Access Address    Tag   Index Offset Result Memrefs");
		System.out.println("---- ------ -------- ------- ----- ------ ------ -------");
    	for(int i = 0; i < references.size(); i++) {
    		System.out.printf("%4d %6s %8s %7s %5d %6d %6s %7d\n", i+1, references.get(i).getAccessType(), references.get(i).getHexAddress(),
    				blocks.get(i).getTag(), blocks.get(i).getIndex(), blocks.get(i).getOffset(), blocks.get(i).getHit(), blocks.get(i).getMemrefs());
    	}
	}
	
	//This function just dumps the current state of the cache for debugging purposes
	public static void dumpCache() {
	    for(int i = 0; i < blockArray.length; i++) {
	        for(int j = 0; j < blockArray[i].length; j++) {
	        	if(blockArray[i][j] != null)
	            System.out.println("Value at blockArray["+i+"]["+j+"] is " + "tag:"  + blockArray[i][j].getTag()
	            		+ " dirty:" + blockArray[i][j].getDirtyBit() + " valid:" + blockArray[i][j].getValidBit());
	        }
	    }
	    System.out.println("----------------");
	}
}

//A reference class used for reading/formatting all the information from a data file
class Reference {
	
	private Character accessType;
	private int sizeOfReference;
	private int hexAddress;
	
	public Reference(Character accessType, int sizeOfReference, int hexAddress) {
		this.accessType = accessType;
		this.sizeOfReference = sizeOfReference;
		this.hexAddress = hexAddress;
	}
	
	public String getAccessType() {
		if(accessType == 'R') {
			return "read";
		} 
		if(accessType == 'W') {
			return "write";	
		}
		return null;
	}
	
	public String getHexAddress() {
		return Integer.toHexString(hexAddress);
	}
}

/* A block class the hold all the individual values held in an actual block in the cache.
 * I created a 2d array of these objects in order to simulation the cache.
 * This class also has functions to flip valid bits, dirty bits, set the number of memrefs, etc.
 */
class Block {
	
	private int tag;
	private int index;
	private int offset;
	private int tagSize;
	private int indexSize;
	private int offsetSize;
	
	private boolean hit;
	private int validBit = 0;
	private int dirtyBit = 0;
	private int memoryRefs = 0;
	
	public Block(String hexAddress, int lineSize, int numberOfSets, int dataReferenceSize) {
		//convert the hexAddress to decimal
		int decimalAddress = Integer.parseInt(hexAddress,16);
		//convert the decimal address to a binary string
		String binaryAddress = Integer.toBinaryString(decimalAddress);
		//set the line size to the dataReference size read in bytes times 8 for the number of bits
		String line = String.format("%" + (dataReferenceSize * 8) + "s", binaryAddress).replace(' ', '0');
		this.offsetSize = (int) (Math.log(lineSize) / Math.log(2));
		this.indexSize = (int) (Math.log(numberOfSets) / Math.log(2));
		this.tagSize = (dataReferenceSize * 8) - offsetSize - indexSize;
		offset = Integer.parseInt(line.substring(tagSize + indexSize, line.length()), 2);
		index = Integer.parseInt(line.substring(tagSize, tagSize + indexSize), 2);
		tag = Integer.parseInt(line.substring(0, tagSize), 2);
	}
	
	public String getTag() {
		return Integer.toHexString(tag);
	}
	
	public int getIndex() {
		return index;
	}
	
	public int getOffset() {
		return offset;
	}
	
	public int getValidBit() {
		return validBit;
	}
	
	public int getDirtyBit() {
		return dirtyBit;
	}
	
	public void setHit(boolean hit) {
		this.hit = hit;
	}
	
	public String getHit() {
		if(hit == true) {
			return "hit";
		} else {
			return "miss";
		}
	}
	
	public void setMemrefs(int memoryRefs) {
		this.memoryRefs = memoryRefs;
	}
	
	public int getMemrefs() {
		return memoryRefs;
	}
	
	public void flipValidBit() {
		if(validBit == 0) {
			validBit = 1;
		} else {
			validBit = 0;
		}
	}
	
	public void flipDirtyBit() {
		if(dirtyBit == 0) {
			dirtyBit = 1;
		} else {
			dirtyBit = 0;
		}
	}
	
	public void markDirty() {
		dirtyBit = 1;
	}
	
	public void markValid() {
		validBit = 1;
	}

}

