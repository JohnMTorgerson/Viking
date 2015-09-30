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
    
    // we'll need to calculate attack plans in the placeArmies phase and remember them during the attackPhase
    // so we'll store those plans in this variable
    protected ArrayList<int[]> takeoverPlan;
    
    public Viking()
    {
        rand = new Random();
        takeoverPlan = new ArrayList<int[]>();
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
        String[] topics = {
            "placeInitialArmies",
            "placeArmies",
            "attackPhase",
            
//            "continentFitness",
            "getAreaTakeoverPaths",
//            "findAreaPaths",
//            "pickBestTakeoverPaths",
            "nothing"
        };
        
        for (int i=0; i<topics.length; i++) {
            if (topic == topics[i]) {
                board.sendChat(message);
            }
        }
    }
    
    // pick initial countries at the beginning of the game. We will do this later.
    public int pickCountry()
    {
        return -1;
    }
    
    // place initial armies at the beginning of the game
    public void placeInitialArmies( int numberOfArmies ) {
        testChat("placeInitialArmies", "*********** PLACE INITIAL ARMIES ***********");
        
        // for now, all we're going to do is dump all our armies on one country
        // to try to take over a single continent. basically, exactly as we did in placeArmies()
        // so we're actually just going to call placeArmies() for now
        placeArmies(numberOfArmies);
    }
    
    public void cardsPhase( Card[] cards ) {
    }
    
    // place armies at the beginning of each turn
    public void placeArmies( int numberOfArmies ) {
        testChat("placeArmies", "*********** PLACE ARMIES ***********");
        
        // first we'll decide what continents to pursue and thus which ones we'll place armies on
        
        int[] bestContList = rateContinents(); // get ordered list of best continents to pursue
        int goalCont = -1;
        // pick the best continent that we don't already own:
        for (int i=0; i<bestContList.length; i++) { // loop through continents from best to worst
            // as soon as we find one that we don't own, pick it and stop looking
            if (BoardHelper.playerOwnsContinent(ID, bestContList[i], countries) == false) {
                goalCont = bestContList[i];
                break;
            }
        }
        
        testChat("placeArmies","goalCont: " + board.getContinentName(goalCont));
        
        // next we need to pick a country to place our armies on in order to take over the continent
        // the getContTakeoverPath function will simulate taking over the continent
        // from multiple countries and give us back the best path to do so
        // the first country in that path list is the one we want to place our armies on
        int[] goalContCountries = getCountriesInContinent(goalCont, countries); // put all the countries from the goal cont into an integer array to pass to the getAreaTakeoverPaths function
        takeoverPlan = getAreaTakeoverPaths(goalContCountries); // get the best paths to take over the goalCont; takeoverPlan is a global ArrayList<int[]> variable
        
        String[] countryNames = getCountryNames(takeoverPlan.get(0));
        testChat("placeArmies", "Path we picked: " + Arrays.toString(countryNames));
        
        // for now, just place all of our armies on the starting country of the path we picked
        int startCountry = takeoverPlan.get(0)[0];
        if (countries[startCountry].getOwner() == ID) { // we should own it, but just in case
            board.placeArmies(numberOfArmies, startCountry);
        }
    }
    
    // attack!
    public void attackPhase() {
        testChat("attackPhase", "*********** ATTACK PHASE ***********");
        
        // for now, all we're doing here is following the single route we calculated in placeArmies()
        // we'll just attack from the beginning to the end of the route as long as we have armies
        // left to keep on attacking
        
        // get the attack route we want to pursue, which was calculated in the placeArmies() phase
        int[] attackRoute = takeoverPlan.get(0);
        
        String[] countryNames = getCountryNames(attackRoute);
        testChat("attackPhase", "Attack Route: " + Arrays.toString(countryNames));
        
        // loop through the whole route
        for(int i=0; i<attackRoute.length-1; i++) {
            if (countries[attackRoute[i]].getArmies() > 1) { // if we have >1 army in the attacking country
                board.attack(attackRoute[i],attackRoute[i+1],true); // attack the next country in the route
            } else {
                break;
            }
        }
    }
    
    // decide how many armies to move upon a successful attack
    public int moveArmiesIn( int cca, int ccd)
    {
        // for now, always move all available armies into the conquered country
        return countries[cca].getArmies() - 1;
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
    protected ArrayList getAreaTakeoverPaths(int[] countryList) {
        testChat("getAreaTakeoverPaths", "-- GET AREA TAKEOVER PATHS --");
        testChat("getAreaTakeoverPaths", "startCountry not given");
        
        // we'll store all candidate paths here (individual paths are integer arrays)
        ArrayList<int[]> paths = new ArrayList<int[]>();

        // we'll test every path from every country we own in the countryList
        // if we don't own any countries in countryList, we'll find a country close-by to start from
        
        int[] candidates = getPlayerCountriesInArea(ID, countryList, countries); // get countries in countryList that we own

        if (candidates.length > 0) { // if we own any countries in countryList
            
            // just testing to see if we found the countries we own
            String[] countryNames = getCountryNames(candidates);
            testChat("getAreaTakeoverPaths", "countries we own in goalCont: " + Arrays.toString(countryNames));
            
            // loop through candidates array, finding paths for each of them
            int[] initialPath = new int[1];
            for (int i=0; i<candidates.length; i++) {
                initialPath[0] = candidates[i];
                paths.addAll(findAreaPaths(initialPath, countryList)); // concatenate results from all of them together in the paths ArrayList
            }
        }
        else { // we don't own any countries in countryList
            testChat("getAreaTakeoverPaths", "we don't own any countries in goalCont");
            
            // find the country outside of countryList that we own with the cheapest path to it
            int[] initialPath = getCheapestRouteToArea(countryList);
            
            String[] countryNames = getCountryNames(initialPath);
            testChat("getAreaTakeoverPaths", "Path to continent: " + Arrays.toString(countryNames));
            
            // use that as starting country
            paths = findAreaPaths(initialPath, countryList);
        }
        
        // now we have a complete list of terminal paths from every country we own in the area (countryList)
        // (or from a nearby country in the case that we don't own any in the area itself)
        // but we still need to go over all of the countries we have paths to, and see if any are left out.
        // this will happen in the case of non-contigious areas, i.e. "you can't get there from here"
        // so we'll check if any of the countries in countryList are not in any of the paths we found
        // and repackage just them as a new area, call getAreaTakeoverPaths() recursively on that area
        // and add the results to the paths ArrayList
        ArrayList<Integer> countriesLeft = new ArrayList<Integer>(); // (note that countriesLeft is an ArrayList, which is atypical (usually integer arrays are used))
        for (int i=0; i<countryList.length; i++) { // create ArrayList of whole area
            countriesLeft.add(countryList[i]);
        }
        int pathsSize = paths.size();
        iLoop: for (int i=0; i<pathsSize; i++) { // loop through all paths
            int[] path = paths.get(i);
            for (int j=0; j<path.length; j++) { // loop through all countries in path
                for (int k=0; k<countriesLeft.size(); k++) { // loop through all countries in area that we haven't seen yet (that's what countriesLeft represents)
                    if (path[j] == countriesLeft.get(k)) { // if this country is in a path
                        countriesLeft.remove(k); // remove it from countriesLeft
                        break; // and continue checking the rest of them
                    }
                }
                if (countriesLeft.size() == 0) { // if this is empty, every country in area is accounted for in paths
                    break iLoop; // so stop looking, we're done
                }
            }
        }
        
        // now anything left in countriesLeft was not accounted for in any path we found
        // (which should only happen if the original area we searched was not contiguous and we didn't own a country in at least one of the discrete parts)
        // so convert it into an integer array and pass it into getAreaTakeoverPaths() recursively as a new area to search
        // then we'll add the results of that function call to the paths we already found
        if (countriesLeft.size() > 0) {
            int[] countriesLeftArray = new int[countriesLeft.size()];
            for (int i=0; i<countriesLeftArray.length; i++) {
                countriesLeftArray[i] = countriesLeft.get(i).intValue();
            }
            String[] countryNames = getCountryNames(countriesLeftArray);
            testChat("getAreaTakeoverPaths", "countriesLeft: " + Arrays.toString(countryNames));
            
            // right now, the next line is causing an infinite loop, pending bug fix in getCheapestRouteToArea()
//            paths.addAll(getAreaTakeoverPaths(countriesLeftArray));
            
        } else {
            testChat("getAreaTakeoverPaths", "all countries were accounted for in the list of paths we found");
        }

        
        testChat("getAreaTakeoverPaths", "There are " + paths.size() + " terminal paths");
        
        // choose and return the best paths
        return pickBestTakeoverPaths(paths, countryList);
    }
    // overload getAreaTakeoverPaths to allow a double parameter version
    // in this version, a starting country is supplied, and we'll only search for paths starting from that country
    protected ArrayList getAreaTakeoverPaths(int[] countryList, int startCountry) {
        testChat("getAreaTakeoverPaths", "-- GET AREA TAKEOVER PATHS --");
        testChat("getAreaTakeoverPaths", "startCountry is " + startCountry);
        
        // we'll store all candidate paths here (individual paths are integer arrays)
        ArrayList<int[]> paths = new ArrayList<int[]>();
        
        // create an new history (integer array) containing only the starting country
        int[] initialPath = new int[1];
        initialPath[0] = startCountry;
        
        // find paths
        paths = findAreaPaths(initialPath, countryList);
        
        // choose and return the best paths
        return pickBestTakeoverPaths(paths, countryList);
    }
    
    // find the nearest country owned by ID and return the cheapest path
    // from it to a country in the given area (area could be a continent, for example)
    protected int[] getCheapestRouteToArea(int[] area, int ID) {
        // eventually this will be a custom written function to find the cheapest path to the given area
        // for the moment, however, we'll just assume that the area is the continent that contains the first country in the area array
        // this definitely will need to get changed at some point

        int continent = countries[area[0]].getContinent();
        int[] path = BoardHelper.cheapestRouteFromOwnerToCont(ID, continent, countries);

        return path;
    }
    // overload getCheapestRouteToArea to allow a single parameter version
    // if no ID is provided, assume it should be the owner
    protected int[] getCheapestRouteToArea(int[] area) {
        return getCheapestRouteToArea(area, ID);
    }
    
    // given a list of all possible takeover paths (allPaths) through a given country list (area)
    // this function should find a comprehensive set of paths that pass through every enemy country in the area
    // ideally, it will find as few as possible that contain every enemy country
    // so far, however, all we're doing is picking one path. eventually, we'll add code to find the rest of them
    protected ArrayList pickBestTakeoverPaths(ArrayList<int[]> allPaths, int[] area) {
        ArrayList<int[]> results = new ArrayList<int[]>();
        
        // find the length of the longest path
        int maxPathLength = 0;
        int size = allPaths.size();
        for (int i=0; i<size; i++) {
            if (allPaths.get(i).length > maxPathLength) {
                maxPathLength = allPaths.get(i).length;
            }
        }
        
        // populate a new arraylist with all the longest paths
        ArrayList<int[]> longestPaths = new ArrayList<int[]>();
        for (int i=0; i<size; i++) {
            if (allPaths.get(i).length == maxPathLength) {
                longestPaths.add(allPaths.get(i));
            }
        }
        
        // display the whole list for testing purposes:
//        for (int i=0; i<allPaths.size(); i++) {
//            String[] countryNames = getCountryNames(allPaths.get(i));
//            testChat("pickBestTakeoverPaths", Arrays.toString(allPaths.get(i)));
//        }
        
        // pick a path that ends in a border, if possible
        // eventually we'll be more sophisticated about this, but for now, this will do
        size = longestPaths.size();
        int pathLength;
        boolean isBorder;
        testChat("pickBestTakeoverPaths", "--- Longest paths: ---");
        for (int i=0; i<size; i++) {
            pathLength = longestPaths.get(i).length;
            isBorder = isAreaBorder(longestPaths.get(i)[pathLength-1],area);
            
            String[] countryNames = getCountryNames(longestPaths.get(i));
            testChat("pickBestTakeoverPaths", Arrays.toString(countryNames) + " border? " + isBorder);
            
            // for now, we'll just return the first one we find that ends in a border
            if (isBorder) {
                results.add(longestPaths.get(i));
                return results;
            }
        }
        
        // if we get here, none of the longest paths ended on a border, so for now, just return the first one
        results.add(longestPaths.get(0));
        return results;
    }
    
    // checks to see if country is a border of area by seeing if any of its
    // neighbors is outside of area
    boolean isAreaBorder (int country, int[] area) {
        int[] neighbors = countries[country].getAdjoiningCodeList(); // get neighbors
        boolean inArea = false;
        
        for (int i=0; i<neighbors.length; i++) { // loop through all the country's neighbors
            inArea = false;
            for (int j=0; j<area.length; j++) { // loop through every country in area
                if (neighbors[i] == area[j]) { // if we found this neighbor in the area
                    inArea = true;
                    break;
                }
            }
            if (!inArea) { // if inArea is false, then this neighbor is not in the area
                return true; // which means country is a border, so return true
            }
        }
        return false; // if we got here, all of the neighbors were in area, so country is not a border; return false
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

        // loop through all neighbors; if valid, add to history and recurse
        for (int i=0; i<neighbors.length; i++) {
            if (pathNeighborIsValid(neighbors[i], history, countryList)) { // if the country is valid
                anyValidNeighbors = true;
                newHistory[newHistory.length-1] = neighbors[i]; // add it to the end of the new history
                terminalPaths.addAll(findAreaPaths(newHistory, countryList)); // recurse, adding whole chain to the terminalPaths array
            }
        }
        
        // if there were no valid neighbors, we're at the end of the path
        // the history as given includes the terminal country of the path
        // so all we have to do is send back the history we were given, wrapped in an arrayList
        // (we'll add it to terminalPaths, which should be empty in this case)
        // and as it bubbles up, it will be concatenated with any other terminal paths that were found
        // in other branches
        if (anyValidNeighbors == false) {
            // make a copy of history to add to terminalPaths to avoid reference/scope problem
            int[] historyCopy = new int[history.length];
            System.arraycopy(history, 0, historyCopy, 0, history.length);
            
            terminalPaths.add(historyCopy);
        }
        
        // return the terminalPaths arrayList. if we're at the end of a path, this will contain
        // a single terminal path. If we're not at the end of a path, it will contain all the terminal
        // paths below us that were found recursively, which will then be concatenated with any other
        // terminal paths that were found elsewhere (i.e. the branches that split above us) as they bubble up
//        for (int i=0; i<terminalPaths.size(); i++) {
//            String[] countryNames = getCountryNames(terminalPaths.get(i));
//            testChat("findAreaPaths", "s" + Arrays.toString(countryNames));
//        }
//        testChat("findAreaPaths", "----- RETURN! -----");
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
        // (2) it's not a country. In case we get passed values that aren't countries, they should be deemed invalid
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
            fitness = (bonus * (numCountriesOwned + 1) * (numArmiesOwned + 1)) / ((float) Math.pow(numCountries,1) * (float) Math.pow(numBorders,1) * (numEnemyArmies + 1));
//            fitness = numCountries; // pick biggest continent for testing purposes
            
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
    // ID is the player ID to check; cont is the continent in question; countries is the global list of all countries on the board
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
    // ID is the player ID to check; area is the list of countries to search in; countries is the global list of all countries on the board
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
