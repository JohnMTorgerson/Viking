package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;
import com.sillysoft.lux.util.*;
import java.util.*;

//
//  Viking.java
//
//  Viking is written by John Torgerson and Jesse Halvorsen
//


public class Viking implements LuxAgent
{
    // This agent's ownerCode:
    protected int ID;
    
    // Store some refs the board and to the country array
    protected Board board;
    protected Country[] countries;
    
    // It might be useful to have a random number generator
    protected Random rand;
    
    public Viking()
    {
        rand = new Random();
    }
    
    // Save references
    public void setPrefs( int newID, Board theboard )
    {
        ID = newID;		// this is how we distinguish what countries we own
        
        board = theboard;
        countries = board.getCountries();
    }
    
    public String name()
    {
        return "Viking";
    }
    
    public float version()
    {
        return 1.0f;
    }
    
    public String description()
    {
        return "Viking is a bot";
    }
    
    protected void testChat(String topic, String message) {
//        if (topic == "continentFitness") { board.sendChat(message); }
        if (topic == "placeInitialArmies") { board.sendChat(message); }
        if (topic == "getAreaTakeoverPath") { board.sendChat(message); }
        if (topic == "findAreaPaths") { board.sendChat(message); }
//        if (topic == "pickBestTakeoverPath") { board.sendChat(message); }

    }
    
    // pick initial countries at the beginning of the game. We will do this later.
    public int pickCountry()
    {
        return -1;
    }
    
    // place initial armies at the beginning of the game
    public void placeInitialArmies( int numberOfArmies )
    {
        // first we'll decide what continents to pursue and thus which ones we'll place armies on
        int[] bestContList = rateContinents(); // get ordered list of best continents to pursue
        int goalCont = bestContList[0]; // pick the best one from that list (eventually we'll be more sophisticated about this)
//        int goalCont = 12; // manually picking a continent for testing purposes
        
        testChat("placeInitialArmies","goalCont: " + board.getContinentName(goalCont));
        
        // next we need to pick a country to place our armies on in order to take over the continent
        // the getContTakeoverPath function will simulate taking over the continent
        // from multiple countries and give us back the best path to do so
        // the first country in that path list is the one we want to place our armies on
        int[] goalContCountries = getCountriesInContinent(goalCont, countries); // put all the countries from the goal cont into an integer array to pass to the getAreaTakeoverPath function
        int[] contTakeoverPath = getAreaTakeoverPath(goalContCountries); // get the best path to take over the goalCont
        
    }
    
    public void cardsPhase( Card[] cards )
    {
    }
    
    public void placeArmies( int numberOfArmies )
    {
    }
    
    public void attackPhase()
    {
    }
    
    public int moveArmiesIn( int cca, int ccd)
    {
        return 0;
    }
    
    public void fortifyPhase()
    {
    }
    
    // called when we win the game
    public String youWon()
    { 
        // For variety we store a bunch of answers and pick one at random to return.
        String[] answers = new String[] {
            "I won",
            "beees?!"
        };
        
        return answers[ rand.nextInt(answers.length) ];
    }
    
    public String message( String message, Object data )
    {
        return null;
    }
    
    /*
     *   ********* HELPER / CUSTOM FUNCTIONS *********
     */
    
    // will return a path of attack from a country (optionally supplied by passing startCountry)
    // to take over as many countries as possible in the given countryList (countryList may be a continent, for example, but doesn't have to be)
    // if startCountry is not provided, will test all the starting countries we own in the countryList and choose the best one
    // if we don't own any countries in the countryList, we'll find one nearby to start on
    protected int[] getAreaTakeoverPath(int[] countryList, int startCountry) {
        ArrayList paths = new ArrayList(); // we'll store all candidate paths here (individual paths are integer arrays)
        
        if (startCountry != -1) { // a startCountry was supplied, so find all paths starting from that country only
            testChat("getAreaTakeoverPath", "startCountry is " + startCountry);
            
            // create an new history (integer array) containing only the starting country
            int[] initialPath = new int[1];
            initialPath[0] = startCountry;
            
            // find paths
            paths = findAreaPaths(initialPath, countryList);
        }
        else { // no startCountry was supplied, so pick our own candidates
            // we'll test every path from every country we own in the countryList
            // if we don't own any countries in countryList, we'll find a country close-by to start from
            testChat("getAreaTakeoverPath", "startCountry not given");
            
            int[] candidates = getPlayerCountriesInArea(ID, countryList, countries); // get countries in countryList that we own

            if (candidates.length > 0) { // if we own any countries in countryList
                
                // just testing to see if we found the countries we own
                String[] countryNames = getCountryNames(candidates);
                testChat("getAreaTakeoverPath", "countries we own in goalCont: " + Arrays.toString(countryNames));
                
                // loop through candidates array, finding paths for each of them
                int[] initialPath = new int[1];
                for (int i=0; i<candidates.length; i++) {
                    initialPath[0] = candidates[i];
                    paths.addAll(findAreaPaths(initialPath, countryList)); // concatenate results from all of them together in the paths ArrayList
                }
            }
            else { // we don't own any countries in countryList
                testChat("getAreaTakeoverPath", "we don't own any countries in goalCont");
                // find the country outside of countryList that we own with the cheapest path to it
                // use that as starting country
            }
        }
        
        testChat("getAreaTakeoverPath", "There are " + paths.size() + " terminal paths");
        
        // choose and return the best path
        return pickBestTakeoverPath(paths);
    }
    // overload getAreaTakeoverPath to allow a single parameter version
    protected int[] getAreaTakeoverPath(int[] countryList) {
        return getAreaTakeoverPath(countryList, -1);
    }
    
    protected int[] pickBestTakeoverPath(ArrayList<int[]> allPaths) {
        
        // sort list of all paths by the length of each path in descending order
//        Collections.sort(allPaths, new intArrayLengthComp()); // this method wasn't working
//        testChat("pickBestTakeoverPath", "sorted paths number: " + allPaths.size());
        
        // display the whole list for testing purposes:
        for (int i=0; i<allPaths.size(); i++) {
    //        String[] countryNames = getCountryNames(allPaths.get(i));
            testChat("pickBestTakeoverPath", Arrays.toString(allPaths.get(i)));
        }
        
        return new int[] {0,0,0,0};
    }
    
    // find all possible paths through enemy countries within countryList
    // starting with the last country in the history array
    // history is an array of country codes containing the path history already searched
    // countryList is an array of country codes in which the entire search takes place
    // this may typically be a continent, but doesn't have to be
    // returns an ArrayList of paths (which are integer arrays)
    // the function calls itself recursively
    protected ArrayList findAreaPaths(int[] history, int[] countryList) {
        ArrayList<int[]> terminalPaths = new ArrayList<int[]>(); // all possible terminal paths will end up in this array
        int startCountry = history[history.length - 1]; // starting country is the last element in the history
        int[] newHistory = new int[history.length + 1]; // new history array to add the next country(s) to
        System.arraycopy(history, 0, newHistory, 0, history.length); // copy the old history into the beginning of new history, leaving one empty spot at the end
        
        int[] neighbors = countries[startCountry].getAdjoiningCodeList(); // get list of startCountry's neighbors
        boolean anyValidNeighbors = false; // if we find any valid neighbors, we'll switch this to true
        ArrayList<int[]> tempPaths = new ArrayList<int[]>();
        
        String[] countryNames = getCountryNames(neighbors);
//        testChat("findAreaPaths", "startCountry: " + countries[startCountry].getName() + " - neighbors: " + Arrays.toString(countryNames));
        
        // loop through all neighbors; if valid, add to history and recurse
        for (int i=0; i<neighbors.length; i++) {
            if (pathNeighborIsValid(neighbors[i], history, countryList)) { // if the country is valid
                testChat("findAreaPaths", "These are the paths in ***terminalPaths:***");
                for (int j=0; j<terminalPaths.size(); j++) {
                    testChat("findAreaPaths", Arrays.toString(terminalPaths.get(j)));
                }
                anyValidNeighbors = true;
                newHistory[newHistory.length-1] = neighbors[i]; // add it to the end of the new history
//                testChat("findAreaPaths",neighbors[i] + " is valid -- New history: " + Arrays.toString(newHistory));
                
//                terminalPaths.addAll(findAreaPaths(newHistory, countryList)); // recurse, adding whole chain to the terminalPaths array
                tempPaths = findAreaPaths(newHistory, countryList);
                testChat("findAreaPaths", "These are the paths in ~~~tempPaths:~~~");
                for (int j=0; j<tempPaths.size(); j++) {
                    testChat("findAreaPaths", Arrays.toString(tempPaths.get(j)));
                }
                for (int j=0; j<tempPaths.size(); j++) {
                    terminalPaths.add(tempPaths.get(j));
                }
                testChat("findAreaPaths", "Here they are concatenated -------:");
                for (int j=0; j<terminalPaths.size(); j++) {
                    testChat("findAreaPaths", Arrays.toString(terminalPaths.get(j)));
                }
                
            } else {
//                testChat("findAreaPaths",countryNames[i] + " is NOT valid");
            }
            
        }
        
        // if there were no valid neighbors, we're at the end of the path
        // the history as given includes the terminal country of the path
        // so all we have to do is send back the history we were given, wrapped in an arrayList
        // (we'll add it to terminalPaths, which should be empty in this case)
        // and as it bubbles up, it will be concatenated with any other terminal paths that were found
        // in higher function calls
        if (anyValidNeighbors == false) {
            
            history[0] = rand.nextInt(899) + 100; // testing
            
            // make a copy of history to add to terminalPaths to avoid reference/scope problem
            int[] historyCopy = new int[history.length];
            System.arraycopy(history, 0, historyCopy, 0, history.length);
            
            terminalPaths.add(historyCopy);
        //    testChat("findAreaPaths", "Terminal Path: " + Arrays.toString(getCountryNames(history)));
        }
        
        // return the terminalPaths arrayList. if we're at the end of a path, this will contain
        // a single terminal path. If we're not at the end of a path, it will contain all the terminal
        // paths below us that were found recursively, which will then be concatenated with any other
        // terminal paths that were found elsewhere (i.e. the branches that split above us) as they bubble up
        for (int i=0; i<terminalPaths.size(); i++) {
            //        String[] countryNames = getCountryNames(terminalPaths.get(i));
            //testChat("findAreaPaths", "s" + Arrays.toString(terminalPaths.get(i)));
        }
        //testChat("findAreaPaths", "----- RETURN! -----");
        return terminalPaths;
    }
    
    // called by the findAreaPaths function to determine whether a potential country in a path is valid
    // i.e. we don't own it, it hasn't been visited already, and it's in the specified list of countries (e.g. a certain continent)
    protected boolean pathNeighborIsValid(int neighbor, int[] history, int[] countryList) {

        // first check if we own the country
        if (countries[neighbor].getOwner() == ID) {
            return false; // if we own it, it's invalid, so return false immediately
        }

        // next, check if the neighbor has already been visited (i.e. it's in the history)
        // it's probably faster to iterate backwards from the end of the array, since a neighbor
        // we've already visited is more likely to be near the end of the array
        for (int i=history.length-1; i>=0; i--) {
            if (history[i] == neighbor) {
                return false; // if in history, it's invalid, so return false immediately
            }
        }
        
        // check if the neighbor is in the allowed list of countries
        for (int i=0; i<countryList.length; i++) {
            if (countryList[i] == neighbor) {
                return true; // if we've gotten this far, all the other checks have passed, so return true
            }
        }
        
        // return false by default. We'll get here in one of two cases I can think of:
        // (1) it's an enemy country that's not in the history and is also not in the countryList, or
        // (2) it's not a country. Sometimes we might get passed values that aren't countries, in which case they should be deemed invalid
        return false;
    }
    
    // function to rate continents to pursue based on several factors,
    // such as size, bonus, number of countries we own, etc...
    protected int[] rateContinents() {
        
        int goalCont = -1; // the continent we will choose
        float bestFitness = 0; // the continent with the best fitness so far
        float fitness; // the fitness of the current continent in each loop
        int numConts = BoardHelper.numberOfContinents(countries); // number of continents
        // declare the factors for each continent from which we'll calculate the fitness
        float bonus, numCountriesOwned, numArmiesOwned, numCountries, numEnemyArmies, numBorders;
        
        Map fitnessMap = new HashMap(); // key value array to store continent ID along with its fitness
        int[] results = new int[numConts]; // array of continent ID's, which will later be sorted by fitness
        String message = "";
        String name;
        
        // loop through all the continents and calculate their fitness, picking the highest one
        for(int cont = 0; cont < numConts; cont++) {
            
            // get the factors for the continent to calculate the fitness
            bonus = board.getContinentBonus(cont); // bonus
            numCountriesOwned = getPlayerCountriesInContinent(ID, cont, countries).length; // how many countries we own in cont
            numArmiesOwned = BoardHelper.getPlayerArmiesInContinent(ID, cont, countries); // how many of our armies in cont
            numCountries = BoardHelper.getContinentSize(cont, countries); // how many countries in cont
            numEnemyArmies = BoardHelper.getEnemyArmiesInContinent(ID, cont, countries); // how many enemy armies in cont
            numBorders = getSmartContinentBorders(cont, countries).length; // how many border countries
            name = board.getContinentName(cont);
            
            // calculate fitness
//            fitness = (bonus * (numCountriesOwned + 1) * (numArmiesOwned + 1)) / ((float) Math.pow(numCountries,1.3) * (float) Math.pow(numBorders,2) * (numEnemyArmies + 1));
            fitness = numCountries; // pick biggest continent for testing purposes
            
            fitnessMap.put(cont,fitness); // store fitness and ID as a key value pair in the map
            results[cont] = cont; // store continent ID's in this array, will get sorted later
            
            message = message + "\nname: " + name + " ID: " + cont + " fitness: " + fitnessMap.get(cont);
        }
        
        testChat("continentFitness",message);
        
        // bubble sort the results array by fitness
        boolean flag = true;
        int temp;
        float v1, v2;
        while(flag) {
            flag = false;
            for (int i=0; i<results.length-1; i++) {
                v1 = (Float)fitnessMap.get(results[i]);
                v2 = (Float)fitnessMap.get(results[i+1]);
                if (v1 < v2) {
                    temp = results[i];
                    results[i] = results[i+1];
                    results[i+1] = temp;
                    flag = true;
                }
            }
        }
        
        testChat("continentFitness",Arrays.toString(results));
        
        return results;
    }
    
    // method to concatenate integer arrays
    // THIS METHOD HASN'T BEEN TESTED YET!
    protected int[] arrayConcat(int[] a, int[] b) {
        int aLength = a.length;
        int bLength = b.length;
        int[] c= new int[aLength + bLength];
        System.arraycopy(a, 0, c, 0, aLength);
        System.arraycopy(b, 0, c, aLength, bLength);
        return c;
    }
    
    // a custom comparator class to compare integer arrays by length
    // NOT WORKING AT THE MOMENT
/*    class intArrayLengthComp implements Comparator<int[]> {
        public int compare(int[] a, int[] b) {
            if (a.length > b.length) {
                return 1;
            } else if (a.length < b.length) {
                return -1;
            } else {
                return 0;
            }
        }
    }*/
    
    // takes an integer array of country codes and returns a string array of the associated country names
    // primarily useful for testing purposes
    protected String[] getCountryNames(int[] codes) {
        int size = codes.length;
        String[] names = new String[size];
        for (int i=0; i<size; i++) {
            names[i] = countries[codes[i]].getName();
        }
        return names;
    }
    
    // helper function to return an array of the countries a player owns in a given continent
    protected int[] getPlayerCountriesInContinent(int ID, int cont, Country[] countries) {
        // continent iterator returns all countries in 'cont',
        // player iterator returns all countries out of those owned by 'ID'
        // this gives us the list of all the countries we own in the continent
        CountryIterator theCountries = new PlayerIterator(ID, new ContinentIterator(cont, countries));
        
        // Put all the countries we own into an ArrayList
        ArrayList countryArray = new ArrayList();
        while (theCountries.hasNext()) {
            countryArray.add(theCountries.next());
        }
        
        // Put the country code of each of the countries into an integer array
        int size = countryArray.size();
        int[] intArray = new int[size];
        for(int i=0; i < size; i++) {
            intArray[i] = ((Country)countryArray.get(i)).getCode();
        }
        
        return intArray;
    }
    
    // helper function to return an array of the countries a player owns in a given list of countries (area)
    protected int[] getPlayerCountriesInArea(int ID, int[] area, Country[] countries) {
        // loop through all the countries in area; if ID owns them, add them to the results ArrayList
        List<Integer> results = new ArrayList<Integer>();
        for (int i=0; i<area.length; i++) {
            if (countries[area[i]].getOwner() == ID) {
                results.add(area[i]);
            }
        }
        
        // Put the country code of each of the countries into an integer array
        int size = results.size();
        int[] intArray = new int[size];
        for(int i=0; i < size; i++) {
            intArray[i] = results.get(i).intValue();
        }
        
        return intArray;
    }

    // helper function to return an array of the countries in a given continent
    protected int[] getCountriesInContinent(int cont, Country[] countries) {
        // continent iterator returns all countries in 'cont'
        CountryIterator theCountries = new ContinentIterator(cont, countries);
        
        // Put all the countries into an ArrayList
        ArrayList countryArray = new ArrayList();
        while (theCountries.hasNext()) {
            countryArray.add(theCountries.next());
        }
        
        // Put the country code of each of the countries into an integer array
        int size = countryArray.size();
        int[] intArray = new int[size];
        for(int i=0; i < size; i++) {
            intArray[i] = ((Country)countryArray.get(i)).getCode();
        }
        
        return intArray;
    }

    // custom get continent borders function
    protected int[] getSmartContinentBorders(int cont, Country[] countries) {
        // eventually this function will pick borders of the continent that may include countries outside of the continent itself such that the number of borders to defend is smaller.
        // For now, it will just call the regular BoardHelper function to get the actual borders
        return BoardHelper.getContinentBorders(cont, countries);
    }
    
}
