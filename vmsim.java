//vmsim.java created by Samuel Durigon for cs1550 project 4 Spring 2023
import java.util.*;
import java.util.PrimitiveIterator.OfDouble;
import java.io.*;

public class vmsim{

    //some global variables!
    static int frames = 0;
    static String algo = "";
    static int refresh = 0;
    static Scanner input = null;

    //simulation variables
    static int macc = 0;
    static int writes = 0;
    static int pfaults = 0;

    //buffered writer object to make the string outputs faster
    static BufferedWriter output = new BufferedWriter(new OutputStreamWriter(System.out));

    //8KB page size described in project pdf
    static final int PAGE_SIZE = (int)Math.pow(2, 13);

    public static void main(String args[]){
        
        //handle all the arguments
        if (args.length != 5 && args.length != 7){
            System.out.println("Incorrect number of arguments.");
            System.exit(0);

        //if first argument is not -n
        }else if(!args[0].equals("-n")){
            System.out.println("Incorrect first argument. Please use \"-n\"");
            System.exit(0);

        //if third argument is not -a
        }else if(!args[2].equals("-a")){
            System.out.println("Incorrect third argument. Please use \"-a\"");
            System.exit(0);

        //if fifth argument is incorrect
        }else if(args.length == 7 && !args[4].equals("-r")){
            System.out.println("Incorrect 5th argument. Please use \"-r\"");
            System.exit(0);
        }

        //assign the frames variable
        try{
            frames = Integer.parseInt(args[1]);
        }catch (Exception e){
            System.out.println("Incorrect second argument, a number is needed following the \"-n\".");
            System.exit(0);
        }

        // //assign the algorithm variable
        algo = args[3];
        if (!algo.equals("opt") && !algo.equals("fifo") && !algo.equals("2nd") && !algo.equals("nru")){
            System.out.println("Incorrect 4th argument. Please input <opt|fifo|2nd|nru> following the \"-n\".");
            System.exit(0);
        }

        //check all nru specific requirements
        if (algo.equals("nru")){
            //check correct arguments length
            if(args.length != 7){
                System.out.println("Incorrect argument form. NRU algorithm requires a refresh argument.");
                System.exit(0);
            //check for -r for refresh argument
            }else if(!args[4].equals("-r")){
                System.out.println("Incorrect 5th argument, Please use \"-r\"");
                System.exit(0);
            }

            //attempt to assign the refresh argument
            try{
                refresh = Integer.parseInt(args[5]);
            }catch (Exception e){
                System.out.println("Incorrect 6th argument. A refresh integer is required.");
                System.exit(0);
            }
        }

        String fileName;
        //if the algorithm is NRU, check that the scanner file is valid
        if (algo.equals("nru")){
            fileName = args[6];
        //check scanner file for the other algorithms
        }else{
            fileName = args[4];
        }

        try{
            input = new Scanner(new File(fileName));
        }catch (Exception e){
            System.out.println("File not Found.");
            System.exit(0);
        }


        //call the actual aglorithms
        if (algo.equals("fifo")){
            fifo_algo();
        }else if (algo.equals("2nd")){
            second_algo();
        }else if (algo.equals("opt")){
            opt_algo(fileName);
        }else if (algo.equals("nru")){
            nru_algo();
        }
        input.close();
    }//end main

    //My implementation of the Optimal Page Replacement algorithm
    /*
     * Opt creates an ArrayList called "future" to store all of the pagenumbers that will ever be
     * referenced in our trace file. Every time there is meant to be an eviction, it calls 
     * "getFutureDistance" to determine the pageNumber that is currently in RAM that will
     * not be used for the longest amount of time by checking the distance in the ArrayList
     * until its next reference.
     */
    public static void opt_algo(String fileName){

        //track the current line of the trace file
        int startIndex = 0;
        //track the filled spots in RAM
        int ramFilled = 0;

        //create our mock RAM, use a LinkedList Queue for FIFO 
        PTE[] RAM = RAMInitArray();

        //create our mock page table using an array
        PTE[] pageTable = PageTableInit();

        //create our arraylist to hold all future instructions
        List<Integer> future = futureInit(fileName);

        while(input.hasNextLine()){

            //flush the output every 500 instructions
            if(macc%1000 == 0){
                try{
                    output.flush();
                }catch(Exception e){
                    System.out.println("output flush error");
                    System.exit(0);
                }

            }
            //grab the line
            String line = input.nextLine();
            //grab the instruction type
            char i_type = nextInstruction(line);

            //track the access
            if (i_type == 'M'){
                macc += 2;
            }else if (i_type == '='){
                continue;
            }else{
                macc += 1;
            }

            //get the page number from the trace file
            int pageNumber = nextIndex(line);

            if(isHit(pageNumber, pageTable)){
                try{
                    output.write("hit\n");
                }catch(Exception e){
                    System.out.println("output hit error");
                    System.exit(0);
                }

                //if we are modifying the page, set it as dirty
                if (i_type == 'S' || i_type == 'M'){
                    //set page as dirty
                    pageTable[pageNumber].setDirty(true);
                }
            //page fault
            }else{

                pfaults++;

                //if there is an empty spot in RAM
                if(ramFilled < RAM.length){
                    
                    //remove from the head
                    PTE newPage = noEviction(pageTable[pageNumber], RAM[ramFilled], i_type);

                    //add the page back to the list
                    RAM[ramFilled] = newPage;
                    
                    ramFilled++;

                //if there are no empty spots in RAM, figure out what to evict
                }else{
                        
                    int evictPageNumber = getFutureDistance(pageNumber, startIndex, future, RAM);
                    int evictIndex = pageTable[evictPageNumber].getFrame();

                    //evict the page, modify it, and add it to the end of the list
                    PTE newPage = pageEvict(pageTable[pageNumber], RAM[evictIndex], i_type);
                    
                    //put the new page in RAM
                    RAM[evictIndex] = newPage;
                }
            }

            startIndex++;
        }//end while

        stringOutput("Optimal", macc, pfaults, writes);
    }//end method

    //my implementation of the first in, first out algorithm
    /*
     * Represents RAM as a LinkedList and simply removes the head of the LinkedList every
     * time an eviction is needed. It then adds the new page to the end of the LinkedList.
     */
    public static void fifo_algo(){

        //create our mock RAM, use a LinkedList Queue for FIFO 
        LinkedList<PTE> RAM = RAMInitLL();

        //create our mock page table, using an array
        PTE[] pageTable = PageTableInit();

        while(input.hasNextLine()){

            if(macc%1000 == 0){
                try{
                    output.flush();
                }catch(Exception e){
                    System.out.println("output flush error");
                    System.exit(0);
                }

            }

            //grab the line
            String line = input.nextLine();
            //grab the instruction type
            char i_type = nextInstruction(line);

            //track the access
            if (i_type == 'M'){
                macc += 2;
            }else if (i_type == '='){
                continue;
            }else{
                macc += 1;
            }

            //get the page number from the trace file
            int pageNumber = nextIndex(line);

            if(isHit(pageNumber, pageTable)){
                try{
                    output.write("hit\n");
                }catch(Exception e){
                    System.out.println("output hit error");
                    System.exit(0);
                }

                //if we are modifying the page, set it as dirty
                if (i_type == 'S' || i_type == 'M'){
                    pageTable[pageNumber].setDirty(true);
                }

            //page does not exist in RAM
            }else{

                //increment number of page faults
                pfaults++;

                //if there is an empty spot in RAM, fill it
                if (RAM.peek().getIndex() == -1){

                    //remove from the head
                    PTE newPage = noEviction(pageTable[pageNumber], RAM.poll(), i_type);

                    //add the page back to the list
                    RAM.add(newPage);

                //if there are no empty spots in RAM, we need to evict a page
                }else{

                    //evict the page, modify it, and add it to the end of the list
                    PTE newPage = pageEvict(pageTable[pageNumber], RAM.poll(), i_type);
                    //add the page to the end of the list
                    RAM.add(newPage);
                }
            }
        }//end while
        stringOutput("FIFO", macc, pfaults, writes);
    }

    //my implementation of the second chance algorithm, the modified FIFO algorithm
    /*
     * Keeps track of RAM frames with a LinkedList and a Referenced bit for each page in RAM.
     * Every time theres a page Hit, set the reference bit to True to show that it was recently
     * used. Every time there is a miss, evict the first 0 in the LinkedList, and set the 
     * referenced bit of every page you skip over to 0.
     */
    public static void second_algo(){

        //create our mock RAM, use a LinkedList Queue for FIFO 
        LinkedList<PTE> RAM = RAMInitLL();

        //create the mock page table
        PTE[] pageTable = PageTableInit();

        while(input.hasNextLine()){

            if(macc%1000 == 0){
                try{
                    output.flush();
                }catch(Exception e){
                    System.out.println("output flush error");
                    System.exit(0);
                }

            }

            //grab the line
            String line = input.nextLine();
           
            char i_type = nextInstruction(line);
        
            //track the access
            if (i_type == 'M'){
                macc += 2;
            }else if (i_type == '='){
                continue;
            }else{
                macc += 1;
            }

            int pageNumber = nextIndex(line);

            if(isHit(pageNumber, pageTable)){
                try{
                    output.write("hit\n");
                }catch(Exception e){
                    System.out.println("output hit error");
                    System.exit(0);
                }

                //if we are modifying the page, set it as dirty
                if (i_type == 'S' || i_type == 'M'){
                    //set page as dirty
                    pageTable[pageNumber].setDirty(true);
                }
                pageTable[pageNumber].setReferenced(true);

            }else{
                //increment number of page faults
                pfaults++;
                if (RAM.peek().getIndex() == -1){
                    //remove from the head
                    PTE newPage = noEviction(pageTable[pageNumber], RAM.poll(), i_type);
                    //add the page back to the list
                    RAM.add(newPage);
                }else{

                    //check reference bit until 0 is found
                    while(RAM.peek().isReferenced()){

                        //set its reference bit to 0 and move to tail
                        PTE removed = RAM.poll();
                        removed.setReferenced(false);
                        RAM.add(removed);
                    }

                    //evict the page
                    PTE newPage = pageEvict(pageTable[pageNumber], RAM.poll(), i_type);

                    //add the page back to the list
                    RAM.add(newPage);
                    
                }


            }//end hit iff
        }//end while

        stringOutput("Second Chance", macc, pfaults, writes);
    }//end method

    //implementation of the NRU page replacement algorithm
    /*
     * Keeps track of RAM with a LinkedList, and keeps track of a Referenced bit
     * for each page in RAM. When it is time to evict, call "nruEvictIndex" to 
     * determine the page that should be evicted based on a given page's 
     * referenced and dirty bits.
     */
    public static void nru_algo(){

        //track the filled spots in RAM
        int ramFilled = 0;
        //create our mock RAM, use a LinkedList Queue for FIFO 
        PTE[] RAM = RAMInitArray();

        //create our mock page table using an array
        PTE[] pageTable = PageTableInit();

        while(input.hasNextLine()){

            if(macc%refresh == 0){
                refresh(RAM);
            }

            if(macc%500 == 0){
                try{
                    output.flush();
                }catch(Exception e){
                    System.out.println("output flush error");
                    System.exit(0);
                }
            }

            //grab the line
            String line = input.nextLine();
           
            char i_type = nextInstruction(line);
        
            //track the access
            if (i_type == 'M'){
                macc += 2;
            }else if (i_type == '='){
                continue;
            }else{
                macc += 1;
            }

            int pageNumber = nextIndex(line);

            //check for the pageNumber in RAM

            if(isHit(pageNumber, pageTable)){
                try{
                    output.write("hit\n");
                }catch(Exception e){
                    System.out.println("output hit error");
                    System.exit(0);
                }

                //if we are modifying the page, set it as dirty
                if (i_type == 'S' || i_type == 'M'){
                    //set page as dirty
                    pageTable[pageNumber].setDirty(true);
                }
                pageTable[pageNumber].setReferenced(true);

            }else{
                //increment number of page faults
                pfaults++;
                if (ramFilled < RAM.length){
                    //remove from the head
                    PTE newPage = noEviction(pageTable[pageNumber], RAM[ramFilled] , i_type);
                    //add the page back to the list

                    RAM[ramFilled] = newPage;

                    ramFilled++;
                }else{

                    //remove the not recently used 
                    int remove = nruEvictIndex(RAM);

                    //evict the page
                    PTE newPage = pageEvict(pageTable[pageNumber], RAM[remove], i_type);

                    //add the page back to the list
                    RAM[remove] = newPage;
                    
                }
            }
        }
        stringOutput("NRU", macc, pfaults, writes);
    }

    //function to check if a given pagenumber is in given RAM
    //@return the location of the hit in the list. if page fault, return -1
    public static boolean isHit(int pageNumber, PTE[] table){

        if (table[pageNumber].isValid()){
            return true;
        }else return false;
    }

    //add a new page to the RAM when there is an empty spot in the RAM
    public static PTE noEviction(PTE newPage, PTE oldPage, char instruction){

        try{
            output.write("page fault - no eviction\n");
        }catch(Exception e){
            System.out.println("output fault error");
            System.exit(0);
        }

        //if writing to the page, set it as dirty
        if(instruction == 'S' || instruction == 'M'){
            newPage.setDirty(true);
        }else{
            newPage.setDirty(false);
        }

        //set referenced bit since this page is recently used
        newPage.setReferenced(true);

        //set the valid bit
        newPage.setValid(true);

        //set the new page's frame
        newPage.setFrame(oldPage.getFrame());

        return newPage;

    }

    //evict a given page by resetting the old values and setting the new values
    public static PTE pageEvict(PTE newPage, PTE oldPage, char instruction){

        //if the head is dirty, dirty eviction
        if(oldPage.isDirty()){
            writes++;
            try{
                output.write("page fault - evict dirty\n");
            }catch(Exception e){
                System.out.println("output fault error");
                System.exit(0);
            }

        //if the head is not dirty, clean eviction
        }else{
            try{
                output.write("page fault - evict clean\n");
            }catch(Exception e){
                System.out.println("output fault error");
                System.exit(0);
            }
        }

        //set the dirty bit
        if(instruction == 'S' || instruction == 'M'){
            newPage.setDirty(true);
        }else{
            newPage.setDirty(false);
        }
        //since this is a brand new page, set the reference bit to 1
        newPage.setReferenced(true);
        //set the new valid bit
        newPage.setValid(true);
        newPage.setFrame(oldPage.getFrame());

        //reset the old page
        oldPage.setFrame(-1);
        oldPage.setDirty(false);
        oldPage.setReferenced(false);
        oldPage.setValid(false);

        return newPage;
    }

    //take the next instruction (I, S, L, or M) from the input line
    public static char nextInstruction(String line){
        //instruction type
        char instruction = line.charAt(0);
        if (instruction == ' '){
            instruction = line.charAt(1);
        }
        return instruction;
    }

    //take the virtual memory address from the input line and decode it into a page number
    public static int nextIndex(String line){
        //grab the memory address
        String address = line.substring(3, 11);
        long index = Long.decode("0x"+address);

        //calculate the page number index
        int pageNumber = (int)(index/PAGE_SIZE);

        if (pageNumber < 0 || pageNumber > Math.pow(2,20)){
            System.out.println("Page Number out of bounds.");
            System.exit(0);
        }
        return pageNumber;
    }

    //initialize our mock RAM as a LinkedList of frames
    public static LinkedList<PTE> RAMInitLL(){
        LinkedList<PTE> RAM = new LinkedList<PTE>();
        for (int i = 0; i < frames; i++){
            PTE pageFrame = new PTE();
            RAM.add(pageFrame);
        }
        return RAM;
    }

    public static PTE[] RAMInitArray(){
        PTE[] RAM = new PTE[frames];
        for(int i = 0; i < RAM.length; i++){
            PTE page = new PTE();
            page.setFrame(i);
            RAM[i] = page;
        }
        
        return RAM;
    }

    public static PTE[] PageTableInit(){
        PTE[] pageTable = new PTE[(int)Math.pow(2, 19)];
        for (int i = 0; i < pageTable.length; i++){
            PTE page = new PTE();
            page.setIndex(i);
            pageTable[i] = page;
        }
        return pageTable;
    }

    //finds and returns the page number that is furthest away from the current address
    public static int getFutureDistance(int pageNumber, int startIndex, List<Integer> future, PTE[] RAM){
        
        //create an arraylist of all page numbers currently in RAM
        ArrayList<Integer> pages = new ArrayList<>();
        for (PTE temp : RAM){
            pages.add(temp.getIndex());
        }

        //begin to iterate through the list of future addresses
        while(startIndex < future.size()){

            //if an address is found, remove it from the arraylist of pages
            if(pages.contains(future.get(startIndex))){
                pages.remove(future.get(startIndex));
            }

            //if the arraylist of all pages only has one page, that is the farthest page number
            if (pages.size() == 1){
                return pages.get(0);
            }

            startIndex++;
        }
        
        //if there is more than one page number with no future reference, just choose one
        return pages.get(0);
    }

    //Iterates through the trace file and adds all decoded page numbers to a list in the order they are called
    //@retun a List of Integers filled with the page numbers
    public static List<Integer> futureInit(String fileName){

        Scanner futureScanner = null;
        //create a scanner to iterate through the trace file
        try{
            futureScanner = new Scanner(new File(fileName));
        }catch (Exception e){
            System.out.println("File not Found");
            System.exit(0);
        }

        List<Integer> futureList = new ArrayList<Integer>();
        while(futureScanner.hasNextLine()){
            String line = futureScanner.nextLine();
            int pageNumber;
            if(line.charAt(0) == '='){
                continue;
            }else{
                pageNumber = nextIndex(line);
                //add it to the list
                futureList.add(pageNumber);
            }
        }

        futureScanner.close();
        return futureList;
    }

    //reset the reference bits for all items in RAM
    public static void refresh(PTE[] RAM){
        for (PTE temp : RAM){
            temp.setReferenced(false);
        }
    }

    //returns the index of RAM that should be evicted
    public static int nruEvictIndex(PTE[] RAM){
        int removal = 0;
        boolean case1 = false;
        boolean case2 = false;
        boolean case3 = true;
        for(int i = 0; i < RAM.length; i++){

            PTE temp = RAM[i];
            boolean dirty = temp.isDirty();
            boolean referenced = temp.isReferenced();

            //0 and 0, return
            if(!dirty && !referenced){
                return i;
            }
            // 1 and 0
            else if(dirty && !referenced){
                if(!case1){
                    removal = i;
                    case1 = true;
                    case2 = false;
                    case3 = false;
                }
            }
            //0 and 1
            else if(!dirty && referenced){
                if(!case1 && !case2){
                    removal = i;
                    case1 = false;
                    case2 = true;
                    case3 = false;
                }
            }
            //1 and 1
            else if(case3){
                removal = 0;
            }
        }

        return removal;
    }

    public static void stringOutput(String algorithm, int macc, int pfaults, int writes){
        System.out.println("\nAlgorithm: " + algorithm);
        System.out.println("Number of frames: " + frames);
        System.out.println("Total Memory Accesses: " + macc);
        System.out.println("Total Page Faults: " + pfaults);
        System.out.println("Total writes to Disk: " + writes);
        System.out.println();
    }

    //PTE.java created by Samuel Durigon for cs1550 Spring 2023
    //Helper class for page table entries
    public static class PTE{

        //properties of a page table entry
        //-1 means it has yet to be initialized
        //the page #, or index
        private int index;
        //keep track of the frame the page is currently stored in;
        private int frame;
        //the dirty bit
        private boolean dirty;
        //the referenced bit
        private boolean reference;
        //valid bit to tell if the page is currently in RAM or not
        private boolean valid;

        public PTE(){
            index = -1;
            dirty = false;
            reference = false;
            frame = -1;
            valid = false;
        }

        //getter and setter for index
        public int getIndex(){
            return index;
        }

        public void setIndex(int in){
            index = in;
        }

        public int getFrame(){
            return frame;
        }

        public void setFrame(int in){
            frame = in;
        }

        //getter and setter for Dirty
        public boolean isDirty(){
            return dirty;
        }

        public void setDirty(boolean in){
            dirty = in;
        }

        //getter and setter for reference
        public boolean isReferenced(){
            return reference;
        }

        public void setReferenced(boolean in){
            reference = in;
        }

        public boolean isValid(){
            return valid;
        }

        public void setValid(boolean in){
            valid = in;
        }
    }//end PTE class
    
}//end vmsim class