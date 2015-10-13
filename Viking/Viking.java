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
    
    // a global variable which tells moveArmiesIn() to leave some of its armies behind
    // after successfully attacking a country. attackPhase() will set this to the number of forks
    // that split off from that point when it attacks through the branch point of a fork
    // so that moveArmiesIn() will know how many armies to leave behind
    protected int forkArmies;
    
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
            "moveArmiesIn",
            
//            "continentFitness",
            "getAreaTakeoverPaths",
//            "findAreaPaths",
//            "pickBestTakeoverPaths",
//            "getCheapestRouteToArea",
            "placeArmiesOnRoutes",
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
        
//        chatContinentNames();
        
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
//        int[] bestContList = new int[] { 3,14,5 }; // interesting continents in U.S.S. Lionhart map
        // pick the best continent that we don't already own:
        int goalCont = -1;
        for (int i=0; i<bestContList.length; i++) { // loop through continents from best to worst
            // as soon as we find one that we don't own, pick it and stop looking
            if (BoardHelper.playerOwnsContinent(ID, bestContList[i], countries) == false) {
                goalCont = bestContList[i];
                break;
            }
        }
        
        testChat("placeArmies","goalCont: " + board.getContinentName(goalCont));
        
        // next we need to pick a country to place our armies on in order to take over the continent
        // the pickBestTakeoverPaths function will simulate taking over the continent
        // from multiple countries and give us back the best set of paths to do so
        // the first country in each path (or route) is the one we want to place our armies on
        int[] goalContCountries = getCountriesInContinent(goalCont, countries); // put all the countries from the goal cont into an integer array to pass to the getAreaTakeoverPaths function
        takeoverPlan = pickBestTakeoverPaths(goalContCountries); // get the best paths to take over the goalCont; takeoverPlan is a global ArrayList<int[]> variable
        
        testChat("placeArmies", "Paths we picked: ");
        chatCountryNames("placeArmies", takeoverPlan);
        
        // loop through the starting country of each path,
        // placing an army on each one until we're out
        // eventually we'll place the armies intelligently, in just the amounts needed to attack the whole route
        // but for now this will do
        while (numberOfArmies > 0) {
            for (int i=0; i<takeoverPlan.size(); i++) {
                int startCountry = takeoverPlan.get(i)[0];
                if (countries[startCountry].getOwner() == ID && numberOfArmies > 0) {
                    board.placeArmies(1, startCountry);
                    numberOfArmies -= 1;
                }
            }
        }
        
        // place only the number of armies needed to takeover the continent on the starting countries of all the paths
        // store any remaining armies available in numberOfArmies
        numberOfArmies = placeArmiesOnRoutes(takeoverPlan,numberOfArmies);

    }
    
    // attack!
    public void attackPhase() {
        testChat("attackPhase", "*********** ATTACK PHASE ***********");
        
        // loop through takeoverPlan (calculated in the placeArmies() phase),
        // which contains multiple attack routes, and execute each one
        for (int i=0; i<takeoverPlan.size(); i++) {
            testChat("attackPhase", "------- Attack route: -------");
            chatCountryNames("attackPhase", takeoverPlan.get(i));
            
            if (countries[takeoverPlan.get(i)[0]].getOwner() == ID) { // if we own the first country in the path
                int[] attackRoute = takeoverPlan.get(i);
                
                testChat("attackPhase", "First country on route has " + countries[attackRoute[0]].getArmies() + " armies.");
                
                // loop through the whole route, attacking as we go
                for(int j=0; j<attackRoute.length-1; j++) {
                    
                    // at each step of the path, before we actually attack
                    // we test for forks. if we find a branch point from this country
                    // then we have to tell moveArmiesIn() to leave some armies behind
                    // in order to take over the fork later from this point
                    forkArmies = 1; // forkArmies is a global variable, which tells moveArmiesIn() to leave some of its armies behind
                    for (int k=i+1; k<takeoverPlan.size(); k++) { // loop through only the rest of the takeoverPlan paths (i.e. the ones we haven't attacked yet) to check for branch points
                        if (attackRoute[j] == takeoverPlan.get(k)[0]) {
                            forkArmies += 1; // increment forkArmies every time we find a different path that branches from this country
                        }
                    }
                    
                    // now we attack
                    if (countries[attackRoute[j]].getArmies() > 1) { // if we have > 1 army in the attacking country
                        board.attack(attackRoute[j],attackRoute[j+1],true); // attack the next country in the route
                    } else {
                        break;
                    }
                }
            } else {
                testChat("attackPhase", "We do not own the starting country of this route");
            }
        }
    }
    
    // decide how many armies to move upon a successful attack
    public int moveArmiesIn( int cca, int ccd)
    {
        // for now, we're always moving everything into the conquered country
        // unless the country we just attacked from is a branch point for one or more forks
        // in which case we will only move a corresponding fraction of the armies
        // so far this does not take into account the armies that are actually needed for each fork
        // it just divides them up evenly so each fork gets the same amount
        
        testChat("moveArmiesIn", "*********** MOVE ARMIES IN ***********");
        
        int armiesOnFrom = countries[cca].getArmies() - 1; // number of armies on the country we just attacked from
        int amountToMove = armiesOnFrom / forkArmies; // move number of armies on the country divided by the number of paths it forks into
        
        testChat("moveArmiesIn", "Attacking country: " + countries[cca].getName() + "\nArmies on attacking country: " + armiesOnFrom + "\nNumber of forks: " + forkArmies + "\nAmount to move: " + amountToMove);
        
        return amountToMove;
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
    
    // called by placeArmies() and placeInitialArmies()
    // given a number of armies and a set of paths to take over, this function calculates the number of armies required to do so
    // and places them appropriately at the starting country of each path
    // it then returns the number of armies leftover
    protected int placeArmiesOnRoutes(ArrayList<int[]> plan, int numberOfArmies) {
        testChat("placeArmiesOnRoutes", "----- Place Armies On Routes -----");
        
        int cost = 0;
        for (int i=0; i<plan.size(); i++) {
            if (countries[plan.get(i)[0]].getOwner() == ID) {
                // calculatePathCost() returns the number of armies it will take to conquer this path
                // not accounting for how many armies we have on the starting country
                cost = calculateCladeCost(plan, i);
                
                chatCountryNames("placeArmiesOnRoutes", plan.get(i));
                testChat("placeArmiesOnRoutes","Cost to take over the above path: " + cost);
                
                // then place cost on the starting country of the path here
                
                // and subtract cost from numberOfArmies
            }
        }
        
//        testChat("placeArmiesOnRoutes", "Total cost to takeover Continent: " + totalCost);

        return numberOfArmies;
    }
    
    // calculates the cost of taking over a clade
    // a clade is a monophyletic tree of paths, i.e. a path and all of its forks (and all of their forks, etc.)
    // clades are not represented in our code as single objects, so this function must find the forks of the initial path itself
    // the function is passed an entire takeover plan ("plan"), which may contain multiple clades, and an index, which tells it which original path in the plan to work on
    // it follows that original path only, and finds all of its forks, and calls itself recursively to find all of its forks' forks, etc.
    // and adds up all of their costs together and returns that number
    protected int calculateCladeCost(ArrayList<int[]> plan, int index) {
        int cost = 0;
        int[] originalPath = plan.get(index);
        cost += calculatePathCost(originalPath);
        //for (int i=1 i<originalPlan.length; i++) {
        //  for (int j=0; j<plan.size(); j++) {
        //      if (originalPlan[i] == plan.get(j)[0]) {
        //          cost += calculateCladeCost(plan, j);
        //      }
        //  }
        //}
        
        return cost;
    }
    
    // calculate the number of armies it will require to take over a whole path (not including the starting country, which we assume we own)
    // does not subtract the number of armies we already have on the first country in the path
    // THIS FUNCTION HAS NOT BEEN TESTED YET
    protected int calculatePathCost(int[] path) {
        // note that "cost" in this function is not simply the number of enemy armies in the way
        // rather, it is an estimate of how many armies it will actually take to conquer the given path
        float cost = 0;
        for (int i=1; i<path.length; i++) { // loop through the path, beginning on the SECOND country
            cost += countries[path[i]].getArmies(); // add enemy armies to the cost
        }
        // here comes the subjective part
        // cost so far contains just the number of enemy armies
        // but this number isn't enough to ensure a successful takeover, so we will add a buffer
        cost *= 1.1;
        cost += path.length - 1;

        return (int) Math.ceil(cost);
    }
    
    // will return a set of all possible terminal attack paths from a country (optionally supplied by passing startCountry)
    // if startCountry is not provided, will find paths from all the starting countries we own in the countryList
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
            paths.addAll(getAreaTakeoverPaths(countriesLeftArray));
            
        } else {
            testChat("getAreaTakeoverPaths", "all countries were accounted for in the list of paths we found");
        }

        
        testChat("getAreaTakeoverPaths", "There are " + paths.size() + " terminal paths");
        
        // choose and return the best paths
        return paths;
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
        return paths;
    }
    
    // find the nearest country owned by ID and return the cheapest path
    // from it to a country in the given area (area could be a continent, for example)
    // this is a modified version of the BoardHelper method cheapestRouteFromOwnerToCont()
    protected int[] getCheapestRouteToArea(int[] area, int owner) {
        
        String[] countryNames = getCountryNames(area);
        testChat("getCheapestRouteToArea", "getCheapestRouteToArea area: " + Arrays.toString(countryNames));
        
        if (owner < 0 || area.length == 0) {
            System.out.println("ERROR in cheapestRouteFromOwnerToCont() -> bad parameters");
            return null;
        }
        
        // first, check to see if we already own a country in the area.
        int[] ownedCountries = getPlayerCountriesInArea(owner, area, countries);
        if (ownedCountries.length > 0) {
            // the player owns a country in the area already. That country itself is the cheapest route
            // so simply return the first one in the list of owned countries
            return new int[] { ownedCountries[0] };
        }
        
        // We keep track of which countries we have already seen (so we don't
        // consider the same country twice). We do it with a boolean array, with
        // a true/false value for each of the countries:
        boolean[] haveSeenAlready = new boolean[countries.length];
        for (int i = 0; i < countries.length; i++)
        {
            haveSeenAlready[i] = false;
        }
        
        // Create a Q (with a history) to store the country-codes and their cost
        // so far:
        CountryPathStack Q = new CountryPathStack();
        
        // We explore from all the borders of <continent>
        int testCode, armiesSoFar;
        int[] testCodeHistory;
        int[] borderCodes = getAreaBorders(area);
        for (int i = 0; i < borderCodes.length; i++) {
            testCode = borderCodes[i];
            armiesSoFar = 0;
            testCodeHistory = new int[1];
            testCodeHistory[0] = testCode;
            haveSeenAlready[testCode] = true;
            
            Q.pushWithValueAndHistory(
                                      countries[borderCodes[i]], 0, testCodeHistory );
        }
        
        // So now we have all the continent borders in the Q (all with cost 0),
        // expand every possible outward path (in the order of cost).
        // eventually we should find a country owned by <owner>,
        // then we return that path's history
        while ( true ) {
            armiesSoFar = Q.topValue();
            testCodeHistory = Q.topHistory();
            testCode = Q.pop();
            
            if ( countries[testCode].getOwner() == owner ) {
                // we have found the best path. return it
                return testCodeHistory;
            }
            
            int[] canAttackInto = BoardHelper.getAttackList(countries[testCode], countries);
            
            for (int i=0; i<canAttackInto.length; i++) {
                if (!haveSeenAlready[canAttackInto[i]]) {
                    // Create the new node's history array. (It is just 
                    // testCode's history with its CC added at the beginning):
                    int[] newHistory = new int[ testCodeHistory.length + 1 ];
                    newHistory[0] = canAttackInto[i];
                    for (int j = 1; j < newHistory.length; j++) {
                        newHistory[j] = testCodeHistory[j-1];
                    }
                    Q.pushWithValueAndHistory( 
                                              countries[canAttackInto[i]], 
                                              // If the neighbor is owned by the proper person then subtract
                                              // its armies from the value so it gets pulled off the Q next.
                                              // Without this there is a bug					
                                              armiesSoFar + (countries[canAttackInto[i]].getOwner() == owner ? -countries[canAttackInto[i]].getArmies() : countries[canAttackInto[i]].getArmies()),
                                              newHistory );
                    haveSeenAlready[ countries[canAttackInto[i]].getCode() ] = true;
                }
            }
            
            // as far as we know, this should only happen in maps with one-way connections
            // if the only country owned by owner is trapped behind a one-way connection from the area
            if (Q.isEmpty()) {
                System.out.println("ERROR in cheapestRouteFromOwnerToCont->can't pop");
                return null;
            }
        }
        // End of cheapestRouteFromOwnerToCont
    }
    // overload getCheapestRouteToArea to allow a single parameter version
    // if no ID is provided, assume it should be the owner
    protected int[] getCheapestRouteToArea(int[] area) {
        return getCheapestRouteToArea(area, ID);
    }
    
    // called in the placeArmies() and placeInitialArmies() phases
    // given that the bot has already chosen a continent (or abstract area) to pursue, this function should find a comprehensive plan
    // to takeover that continent/area in the form of a set of int[] arrays of country codes that form attack paths from countries we own
    // this function calls getAreaTakeoverPaths() to get a list of all possible takeover paths (allPaths) through a given country list (area)
    // then finds a comprehensive set of paths that pass through every enemy country in the area, including forks and islands
    // ideally, it will find as few as possible that contain every enemy country in the area
    protected ArrayList pickBestTakeoverPaths(int[] area) {
        ArrayList<int[]> checkPaths = getAreaTakeoverPaths(area); // first, get comprehensive list of paths; this could be thousands of elements long in large cases
        ArrayList<int[]> results = new ArrayList<int[]>(); // this will hold the results, which could be several paths, to include forks and islands
        ArrayList<Integer> countriesLeft = new ArrayList<Integer>(); // list of countries not in any paths we've chosen so far
        // initially populate countriesLeft with every country in area that we don't own
        for (int i=0; i<area.length; i++) {
            if (countries[area[i]].getOwner() != ID) {
                countriesLeft.add(area[i]);
            }
        }
        
        testChat("pickBestTakeoverPaths", "-- PICK BEST TAKEOVER PATHS --");
        
        // now the meat:
        // in each iteration of this loop, we'll pick a new path out of checkPaths to put in the results arraylist.
        // then, if we've already chosen other paths, we'll need to prune the one we just picked of any overlap with them.
        // there should only ever be overlap at the beginning of the new path, in which case it will be a branch of a fork,
        // so we'll want to store the path only the branch point forward (the place armies and attack phase will know how to deal with that).
        // so after we do all that, next we'll prune the list of potential paths (checkPaths) of any whose last element is contained somewhere within one of the results paths we've already chosen.
        // that way, next time around the loop, we're guaranteed to choose a path that ends in a country we haven't covered yet.
        // finally, we'll prune the countriesLeft arraylist of any countries covered in the path we just picked, so that it only contains countries that aren't in any of the paths we've chosen so far.
        // when countriesLeft is empty, we'll know that we've found a comprehensive set of paths to take over the area, so we'll be done with the loop.
        while (countriesLeft.size() > 0) {
            // find the best single path from the pruned list of paths to check
            int[] newPath = findBestSingleTakeoverPath(checkPaths, area); // see findBestSingleTakeoverPath() for the criteria we use to pick the best path
            
            // now we search for a common element with any of the paths we've already chosen
            // if we find one, this will be a branch point (fork), so we'll truncate everything from the new path before the branch point
            int newStart = 0;
            newPathLoop: for (int i=newPath.length-1; i>=0; i--) { // iterate backwards through newPath
                for (int[] resultsPath : results) { // loop through all the paths we've already picked
                    for (int resultsCountry : resultsPath) { // loop through this path we've already picked
                        if (newPath[i] == resultsCountry) { // if this country in newPath is in one of the old paths
                            newStart = i; // store the index
                            break newPathLoop; // quit searching
                        }
                    }
                }
            }
            // now truncate the beginning of newPath
            // if we didn't find a branch point, it will not be affected
            int[] newPathCut = new int[newPath.length - newStart]; // make new array of appropriate length
            System.arraycopy(newPath, newStart, newPathCut, 0, newPathCut.length); // copy end of newPath into it
            
            testChat("pickBestTakeoverPaths", "-- New path uncut, then cut: ");
            chatCountryNames("pickBestTakeoverPaths", newPath);
            chatCountryNames("pickBestTakeoverPaths", newPathCut);

            // add truncated array to results
            results.add(newPathCut);

            testChat("pickBestTakeoverPaths", "-- Paths we're picking:");
            chatCountryNames("pickBestTakeoverPaths", results);
            
            // prune checkPaths
            // keep all the paths whose last element isn't in any of the chosen paths so far
            // discarding all those whose last element IS in any of the chosen paths so far
            Iterator<int[]> checkPathsIterator = checkPaths.iterator();
            while (checkPathsIterator.hasNext()) { // loop through all the paths in checkPaths
                //testChat("pickBestTakeoverPaths", Arrays.toString(checkPathsIterator.next()));
                int[] thisCheckPath = checkPathsIterator.next(); // the path in checkPaths we're testing this loop
                iLoop1: for (int i=0; i<results.size(); i++) { // loop through all the paths in results
                    int[] resultsPath = results.get(i); // the path in results we're checking against this loop
                    for (int j=0; j<resultsPath.length; j++) { // loop through this path in results
                        if (thisCheckPath[thisCheckPath.length-1] == resultsPath[j]) { // if the last element in thisCheckPath is in resultsPath
                            checkPathsIterator.remove(); // remove thisCheckPath from checkPaths
                            break iLoop1; // move on to next path in checkPaths
                        }
                    }
                }
            }
            testChat("pickBestTakeoverPaths", "-- Pruned list of paths:");
            chatCountryNames("pickBestTakeoverPaths", checkPaths);
        
            // remove any countries in countriesLeft that are in any of the results paths
            Iterator<Integer> countriesLeftIterator = countriesLeft.iterator();
            while (countriesLeftIterator.hasNext()) { // loop through countriesLeft
                int thisCountry = countriesLeftIterator.next();
                iLoop2: for (int i=0; i<results.size(); i++) { // loop through results list
                    int[] thisResult = results.get(i);
                    for (int j=0; j<thisResult.length; j++) { // loop through this result path
                        if (thisResult[j] == thisCountry) { // if this country in countriesLeft is in a results array
                            countriesLeftIterator.remove(); // then remove it from countriesLeft
                            break iLoop2; // and skip to the next country in countriesLeft
                        }
                    }
                }
            }
            testChat("pickBestTakeoverPaths", "-- Pruned version of countriesLeft:");
            if (countriesLeft.size() > 0) {
                chatCountryNames("pickBestTakeoverPaths", countriesLeft);
            } else {
                testChat("pickBestTakeoverPaths", "[] - no countries in countriesLeft");
            }
            testChat("pickBestTakeoverPaths", "--------------------------");
        }

        return results;
    }
    
    // given a list of takeover paths, pick the longest one
    // if there are multiple longest ones (which there often are),
    // find one that ends on a border and return that one
    // in future versions, we may want to be more sophisticated about which one to choose
    protected int[] findBestSingleTakeoverPath(ArrayList<int[]> paths, int[] area) {
        // find the length of the longest path
        int maxPathLength = 0;
        int size = paths.size();
        for (int i=0; i<size; i++) {
            if (paths.get(i).length > maxPathLength) {
                maxPathLength = paths.get(i).length;
            }
        }
        
        // populate a new arraylist with all the longest paths
        ArrayList<int[]> longestPaths = new ArrayList<int[]>();
        for (int i=0; i<size; i++) {
            if (paths.get(i).length == maxPathLength) {
                longestPaths.add(paths.get(i));
            }
        }
        
        // pick a path that ends in a border, if there is one
        size = longestPaths.size();
        int pathLength;
        boolean isBorder;
        testChat("findBestSingleTakeoverPath", "--- Longest paths: ---");
        for (int i=0; i<size; i++) {
            pathLength = longestPaths.get(i).length;
            isBorder = isAreaBorder(longestPaths.get(i)[pathLength-1],area);
            
            String[] countryNames = getCountryNames(longestPaths.get(i));
            testChat("findBestSingleTakeoverPath", Arrays.toString(countryNames) + " border? " + isBorder);
            
            // for now, we'll just return the first one we find that ends in a border
            if (isBorder) {
                return longestPaths.get(i);
            }
        }
        
        // if we get here, none of the longest paths ended on a border, so just return the first one
        return longestPaths.get(0);
    }
    
    // checks to see if country is a border of area by seeing if any of its
    // neighbors is outside of area
    protected boolean isAreaBorder (int country, int[] area) {
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
    
    // returns an int[] of all the borders of the given area
    protected int[] getAreaBorders(int[] area) {
        ArrayList<Integer> borders = new ArrayList<Integer>();
        
        for (int i=0; i<area.length; i++) {
            if (isAreaBorder(area[i],area)) {
                borders.add(area[i]);
            }
        }
        
        int[] bordersArray = new int[borders.size()];
        for (int i=0; i<bordersArray.length; i++) {
            bordersArray[i] = borders.get(i);
        }
        
        return bordersArray;
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
    
    // THE FOLLOWING TWO FUNCTIONS HAVEN'T BEEN TESTED YET:
    
    // chat a list of countries by name, given an array of country codes
    // callingFunc is simply a string to give to the testChat() function to tell it where the chatting is coming from; see testChat() for details
    // useful for testing purposes
    protected void chatCountryNames(String callingFunc, int[] codes) {
        if (codes.length < 1) { return; } // if we're passed an empty array, simply do nothing
        String[] countryNames = getCountryNames(codes);
        testChat(callingFunc, Arrays.toString(countryNames));
    }
    // chat a list, either of arrays of countries (the usual case), or simply of countries (unusual)
    // converting, in both cases, the country codes to country names
    // **NOTE!** this function assumes that all the elements of list are the same type. It may act unexpectedly if they are not
    // callingFunc is simply a string to give to the testChat() function to tell it where the chatting is coming from; see testChat() for details
    // useful for testing purposes
    protected void chatCountryNames(String callingFunc, ArrayList list) {
        if (list.size() < 1) { return; } // if we're passed an empty ArrayList, simply do nothing
        // if the first element in list is an int[], we'll assume they all are, and this is a list of paths
        // so we'll loop through list and chat out each path separately
        if (list.get(0) instanceof int[]) {
            for(int i=0; i<list.size(); i++) {
                if (list.get(i) instanceof int[]) {
                    int[] codesArray = (int[]) list.get(i);
                    chatCountryNames(callingFunc, codesArray);
                }
            }
        }
        // if the first element in list is an integer, we'll assume they all are, and this is a list of countries
        // so we'll convert list into an int[], and chat it out all at once
        if (list.get(0) instanceof Integer) {
            // first convert the ArrayList into an int[] array
            int[] codesArray = new int[list.size()];
            for (int i=0; i<codesArray.length; i++) {
                if (list.get(i) instanceof Integer) {
                    codesArray[i] = (Integer) list.get(i);
                } else {
                    codesArray[i] = -1;
                }
            }
            // then call chatCountryNames using the int[] array
            chatCountryNames(callingFunc, codesArray);
        }
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
    
    // chat out all continent codes and names
    protected void chatContinentNames() {
        int numConts = board.getNumberOfContinents();
        for (int i=0; i<numConts; i++) {
            board.sendChat(i + " - " + board.getContinentName(i));
        }
    }
}
